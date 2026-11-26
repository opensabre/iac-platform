package io.github.opensabre.iac.integration;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.invocation.InvocationHistory;
import io.github.opensabre.iac.shared.IacDataScope;
import org.springframework.ai.tool.ToolCallback;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
import io.github.opensabre.iac.result.llm.ModelFailure;
import io.github.opensabre.iac.result.llm.SnapshotMcpToolProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/** Executes trusted, frozen capability snapshots; business prompts and result interpretation stay with callers. */
@RestController
@RequestMapping("/internal/iac/model")
@RequiredArgsConstructor
public class ModelInvocationController {
    private final ConsumerAccess access;
    private final SnapshotChatModelRouter models;
    private final SnapshotMcpToolProvider tools;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    private final InvocationHistory history;
    private final IacDataScope scope;

    /** Executes the caller's frozen capability and records its outcome under a generated request ID. */
    @PostMapping("/invoke")
    public Reply invoke(@RequestBody Request request) {
        String caller = access.requireConsumer();
        if (request == null || request.snapshot() == null || request.snapshot().toString().length() > 250_000
                || request.messages() == null || request.messages().isEmpty() || request.messages().size() > 40)
            throw IacException.invalidArgument("调用快照或消息为空或超限");
        JsonNode configuration = request.snapshot().path("configJson");
        if (configuration.isTextual()) {
            try { configuration = json.readTree(configuration.asText()); }
            catch (Exception invalid) { throw IacException.invalidArgument("智能体快照格式无效"); }
        }
        if (!"3.0".equals(configuration.path("schemaVersion").asText())
                || !configuration.path("assetSnapshots").path("primaryModel").isObject())
            throw IacException.invalidArgument("必须使用已冻结的纯大模型能力快照");
        List<Message> messages = new ArrayList<>();
        int length = 0;
        for (Turn turn : request.messages()) {
            if (turn == null || turn.content() == null || turn.role() == null)
                throw IacException.invalidArgument("调用消息无效");
            length += turn.content().length();
            if (length > 1_000_000) throw IacException.invalidArgument("调用消息超限，禁止截断");
            messages.add(switch (turn.role()) {
                case "system" -> new SystemMessage(turn.content());
                case "user" -> new UserMessage(turn.content());
                case "assistant" -> new AssistantMessage(turn.content());
                default -> throw IacException.invalidArgument("不支持的消息角色");
            });
        }
        Prompt prompt = new Prompt(messages);
        if (request.businessReference() != null && !request.businessReference().matches("[A-Za-z0-9_-]{1,128}"))
            throw IacException.invalidArgument("任务标识格式无效");
        var snapshot = request.snapshot();
        String agentId = identifier(snapshot.path("agentId"));
        if (agentId == null) agentId = identifier(snapshot.path("id"));
        var execution = history.start("INTERNAL", caller, agentId,
                snapshot.path("versionNo").canConvertToInt() ? snapshot.path("versionNo").asInt() : null,
                null, request.businessReference(), scope.owner(), scope.groupId());
        ChatResponse response;
        try {
            if (request.toolsEnabled()) {
                try (var session = execution.step("MCP", "连接与发现", () -> tools.open(snapshot))) {
                    response = models.call(prompt, snapshot, session.callbacks(), execution);
                }
            } else {
                response = models.call(prompt, snapshot, new ToolCallback[0], execution);
            }
            if (response == null || response.getResult() == null || response.getResult().getOutput().getText() == null)
                throw IacException.invalidState("模型返回为空");
        } catch (RuntimeException failure) {
            var diagnostic = ModelFailure.from(failure);
            execution.failed(diagnostic.name());
            throw IacException.invalidState(diagnostic.message() + "，请求 ID " + execution.requestId());
        }
        execution.succeeded(response);
        var metadata = response.getMetadata();
        var usage = metadata == null ? null : metadata.getUsage();
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        return new Reply(response.getResult().getOutput().getText(), metadata == null ? null : metadata.getModel(),
                reported ? usage.getPromptTokens() : null, reported ? usage.getCompletionTokens() : null, execution.requestId());
    }

    private static String identifier(JsonNode value) {
        String id = value.asText("");
        return id.matches("[A-Za-z0-9_-]{1,64}") ? id : null;
    }

    public record Turn(String role, String content) { }
    public record Request(JsonNode snapshot, List<Turn> messages, boolean toolsEnabled, String businessReference) {
        public Request(JsonNode snapshot, List<Turn> messages, boolean toolsEnabled) {
            this(snapshot, messages, toolsEnabled, null);
        }
    }
    public record Reply(String content, String model, Integer inputTokens, Integer outputTokens, String requestId) { }
}
