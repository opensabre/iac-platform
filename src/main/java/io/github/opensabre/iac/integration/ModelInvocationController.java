package io.github.opensabre.iac.integration;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
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

    @PostMapping("/invoke")
    public Reply invoke(@RequestBody Request request) {
        access.requireConsumer();
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
        ChatResponse response;
        if (request.toolsEnabled()) {
            try (var session = tools.open(request.snapshot())) {
                response = models.call(prompt, request.snapshot(), session.callbacks());
            }
        } else response = models.call(prompt, request.snapshot());
        if (response == null || response.getResult() == null || response.getResult().getOutput().getText() == null)
            throw IacException.invalidState("模型返回为空");
        var metadata = response.getMetadata();
        var usage = metadata == null ? null : metadata.getUsage();
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        return new Reply(response.getResult().getOutput().getText(), metadata == null ? null : metadata.getModel(),
                reported ? usage.getPromptTokens() : null, reported ? usage.getCompletionTokens() : null);
    }

    public record Turn(String role, String content) { }
    public record Request(JsonNode snapshot, List<Turn> messages, boolean toolsEnabled) { }
    public record Reply(String content, String model, Integer inputTokens, Integer outputTokens) { }
}
