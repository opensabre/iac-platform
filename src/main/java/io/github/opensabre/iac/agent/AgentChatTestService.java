package io.github.opensabre.iac.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.invocation.InvocationHistory;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
import io.github.opensabre.iac.result.llm.SnapshotMcpToolProvider;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Stateless execution of test configurations and published Agent snapshots. */
@Service
public class AgentChatTestService {
    private final ObjectMapper json;
    private final AgentAssetReferenceValidator assets;
    private final SnapshotChatModelRouter models;
    private final SnapshotMcpToolProvider tools;
    private final io.github.opensabre.iac.agent.dao.AgentVersionMapper versions;
    public AgentChatTestService(ObjectMapper json, AgentAssetReferenceValidator assets,
                                SnapshotChatModelRouter models, SnapshotMcpToolProvider tools, io.github.opensabre.iac.agent.dao.AgentVersionMapper versions) {
        this.json=json; this.assets=assets; this.models=models; this.tools=tools; this.versions=versions;
    }
    public Result chat(Request request) {
        return chat(request, null);
    }

    /** Executes the validated chat with optional diagnostic measurements from its caller. */
    public Result chat(Request request, InvocationHistory.Execution execution) {
        if (request == null || request.configJson() == null || request.configJson().length() > 100000
                || request.messages() == null || request.messages().isEmpty() || request.messages().size() > 40)
            throw IacException.invalidArgument("请提供智能体配置和 1–40 条聊天消息");
        if ((request.agentId() == null) != (request.versionNo() == null))
            throw IacException.invalidArgument("智能体与版本必须同时指定");
        AgentConfiguration config;
        try { config=json.readValue(request.configJson(), AgentConfiguration.class); }
        catch (Exception error) { throw IacException.invalidArgument("智能体配置格式错误"); }
        // Never trust assetSnapshots supplied by the browser, including credential references.
        if (request.agentId() != null && request.versionNo() != null) {
            var version=versions.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                    .<io.github.opensabre.iac.agent.model.AgentVersion>lambdaQuery()
                    .eq(io.github.opensabre.iac.agent.model.AgentVersion::getAgentId, request.agentId())
                    .eq(io.github.opensabre.iac.agent.model.AgentVersion::getVersionNo, request.versionNo()));
            if (version == null) throw IacException.notFound("智能体版本不存在");
            try { config=json.readValue(version.getConfigJson(), AgentConfiguration.class); }
            catch (Exception error) { throw IacException.invalidArgument("保存版本的配置格式错误"); }
            if (!AgentConfiguration.CAPABILITY_SCHEMA.equals(config.schemaVersion()))
                throw IacException.invalidArgument("不支持的智能体配置，请使用纯大模型能力配置");
            config.validated();
            if (config.assetSnapshots() == null || !("PUBLISHED".equals(version.getStatus()) || "PENDING_APPROVAL".equals(version.getStatus())))
                config=config.withSnapshots(assets.snapshot(config));
        } else {
            if (!AgentConfiguration.CAPABILITY_SCHEMA.equals(config.schemaVersion()))
                throw IacException.invalidArgument("请先配置纯大模型能力后测试聊天");
            config.validated();
            config=config.withSnapshots(assets.snapshot(config));
        }
        return execute(config, request.messages(), execution);
    }

    /** Executes only the frozen assets of a verified published version. */
    Result published(String configJson, String input, InvocationHistory.Execution execution) {
        AgentConfiguration config;
        try {
            config = json.readValue(configJson, AgentConfiguration.class);
        } catch (Exception error) {
            throw IacException.invalidState("已发布的智能体配置格式错误");
        }
        config.validated();
        if (!AgentConfiguration.CAPABILITY_SCHEMA.equals(config.schemaVersion()) || config.assetSnapshots() == null
                || config.assetSnapshots().primaryModel() == null)
            throw IacException.invalidState("已发布版本缺少冻结模型配置");
        return execute(config, List.of(new Turn("user", input)), execution);
    }

    private Result execute(AgentConfiguration config, List<Turn> turns, InvocationHistory.Execution execution) {
        StringBuilder system=new StringBuilder(config.systemPrompt());
        (config.assetSnapshots().skills() == null ? List.<AgentConfiguration.SkillSnapshot>of() : config.assetSnapshots().skills()).forEach(skill -> system.append("\n\nSkill: ").append(skill.name())
                .append("\n").append(skill.instructions()).append("\n输入约定: ").append(skill.inputSchemaJson())
                .append("\n输出约定: ").append(skill.outputSchemaJson()));
        List<Message> messages=new ArrayList<>(); messages.add(new SystemMessage(system.toString()));
        int length=0;
        for (int index=0; index<turns.size(); index++) {
            Turn turn=turns.get(index);
            String expected=index % 2 == 0 ? "user" : "assistant";
            if (turn == null || !expected.equals(turn.role()) || turn.content() == null || turn.content().isBlank())
                throw IacException.invalidArgument("聊天消息必须由用户开始，按用户与助手交替排列");
            length+=turn.content().length();
            if (length > 32000) throw IacException.invalidArgument("聊天上下文超过 32000 字符，请重新开始会话");
            messages.add("user".equals(turn.role()) ? new UserMessage(turn.content()) : new AssistantMessage(turn.content()));
        }
        if (!"user".equals(turns.getLast().role())) throw IacException.invalidArgument("最后一条消息必须来自用户");
        var snapshot=json.createObjectNode().set("configJson", json.valueToTree(config));
        long start=System.nanoTime();
        String phase="MCP 连接或工具发现";
        try (var session = execution == null ? tools.open(snapshot)
                : execution.step("MCP", "连接与发现", () -> tools.open(snapshot))) {
            phase="模型调用";
            List<String> calledTools=new ArrayList<>();
            var callbacks=java.util.Arrays.stream(session.callbacks()).map(callback -> new org.springframework.ai.tool.ToolCallback() {
                public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() { return callback.getToolDefinition(); }
                public String call(String arguments) { calledTools.add(callback.getToolDefinition().name()); try { return callback.call(arguments); } catch (RuntimeException error) { throw IacException.invalidState("MCP 工具调用失败，请检查工具服务与授权配置"); } }
            }).toArray(org.springframework.ai.tool.ToolCallback[]::new);
            var prompt = new Prompt(messages);
            var response = execution == null ? models.call(prompt, snapshot, callbacks)
                    : models.call(prompt, snapshot, callbacks, execution);
            if (response == null || response.getResult() == null || response.getResult().getOutput().getText() == null)
                throw IacException.invalidState("模型未返回回复");
            if (execution != null) execution.succeeded(response);
            return new Result(response.getResult().getOutput().getText(), (System.nanoTime()-start)/1000000,
                    response.getMetadata().getModel(), List.copyOf(calledTools));
        } catch (IacException error) { throw error; }
        catch (RuntimeException error) { throw IacException.invalidState(phase+"失败，请检查对应服务连接、认证引用及超时配置"); }
    }
    public record Request(String configJson, List<Turn> messages, String agentId, Integer versionNo, String sessionId) {
        public Request(String configJson, List<Turn> messages, String agentId, Integer versionNo) { this(configJson,messages,agentId,versionNo,null); }
        public Request(String configJson, List<Turn> messages) { this(configJson,messages,null,null); }
    }
    public record Turn(String role, String content) { }
    public record Result(String content, long latencyMillis, String model, List<String> calledTools) { }
}
