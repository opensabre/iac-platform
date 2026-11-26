package io.github.opensabre.iac.result.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.invocation.InvocationHistory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Routes capability calls through the frozen primary and ordered fallback models. */
@Component
public class SnapshotChatModelRouter {
    private final ObjectMapper objectMapper;
    private final SecretReferenceResolver secrets;
    public SnapshotChatModelRouter(ObjectMapper objectMapper, SecretReferenceResolver secrets) {
        this.objectMapper = objectMapper;
        this.secrets = secrets;
    }

    /** Executes the frozen primary model and ordered fallbacks; no deployment default is substituted. */
    public ChatResponse call(Prompt prompt, JsonNode agentSnapshot) {
        return call(prompt, agentSnapshot, new ToolCallback[0]);
    }
    public ChatResponse call(Prompt prompt, JsonNode agentSnapshot, ToolCallback[] callbacks) {
        return call(prompt, agentSnapshot, callbacks, null);
    }

    /** Runs candidates in order and captures model attempts and tool execution for the current request. */
    public ChatResponse call(Prompt prompt, JsonNode agentSnapshot, ToolCallback[] callbacks,
                             InvocationHistory.Execution execution) {
        if (execution != null) {
            callbacks = java.util.Arrays.stream(callbacks)
                    .map(callback -> measuredCallback(execution, callback)).toArray(ToolCallback[]::new);
        }
        Prompt executablePrompt = callbacks.length == 0 ? prompt : new Prompt(prompt.getInstructions(),
                ToolCallingChatOptions.builder().toolCallbacks(callbacks).build());
        JsonNode config=config(agentSnapshot);
        if (!"3.0".equals(config.path("schemaVersion").asText())
                || !config.path("assetSnapshots").isObject()
                || !config.path("assetSnapshots").path("primaryModel").isObject())
            throw new IllegalStateException("Agent 缺少已发布的纯大模型资产快照");
        List<JsonNode> candidates=new ArrayList<>(); candidates.add(config.path("assetSnapshots").path("primaryModel"));
        config.path("assetSnapshots").path("fallbackModels").forEach(candidates::add);
        RuntimeException last=null;
        for (JsonNode candidate : candidates) {
            try {
                if (execution == null) return create(taskModel(candidate, agentSnapshot)).call(executablePrompt);
                return execution.step("MODEL", candidate.path("modelName").asText(),
                        () -> create(taskModel(candidate, agentSnapshot)).call(executablePrompt));
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        throw last==null?new IllegalStateException("Agent 没有可用模型快照"):last;
    }
    private ToolCallback measuredCallback(InvocationHistory.Execution execution, ToolCallback callback) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return callback.getToolDefinition();
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return callback.getToolMetadata();
            }

            @Override
            public String call(String arguments) {
                return execution.step("TOOL", getToolDefinition().name(), () -> callback.call(arguments));
            }

            @Override
            public String call(String arguments, ToolContext toolContext) {
                return execution.step("TOOL", getToolDefinition().name(), () -> callback.call(arguments, toolContext));
            }
        };
    }

    /** Apply per-task controls to a copy, including every fallback, never to published model snapshots. */
    JsonNode taskModel(JsonNode model, JsonNode snapshot) {
        JsonNode overrides = snapshot == null ? objectMapper.nullNode() : snapshot.path("taskOverrides");
        var copy = model.deepCopy();
        if (overrides.has("temperature") || overrides.has("timeoutSeconds")) {
            if (!copy.isObject()) throw new IllegalStateException("任务模型快照无效");
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) copy;
            if (overrides.has("temperature")) object.set("temperature", overrides.get("temperature"));
            if (overrides.has("timeoutSeconds")) object.set("timeoutSeconds", overrides.get("timeoutSeconds"));
        }
        return copy;
    }

    public ChatModel create(JsonNode model) {
        String provider=model.path("provider").asText();
        if(!List.of("SPRING_AI","OPENAI","DASHSCOPE","OLLAMA").contains(provider)) throw new IllegalStateException("当前 Spring AI 运行时不支持模型供应商: "+provider);
        String apiKey="OLLAMA".equals(provider)&&model.path("secretRef").asText("").isBlank()?"ollama":secrets.resolve(model.path("secretRef").asText());
        OpenAiChatOptions options=OpenAiChatOptions.builder().baseUrl(model.path("endpoint").asText()).apiKey(apiKey)
                .model(model.path("modelName").asText()).temperature(model.path("temperature").asDouble(0.1))
                .timeout(Duration.ofSeconds(model.path("timeoutSeconds").asInt(60))).maxRetries(model.path("maxRetries").asInt(0)).build();
        return OpenAiChatModel.builder().options(options).build();
    }
    private JsonNode config(JsonNode snapshot){if(snapshot==null)return objectMapper.nullNode();JsonNode config=snapshot.path("configJson");if(config.isTextual())try{return objectMapper.readTree(config.asText());}catch(Exception ignored){return objectMapper.nullNode();}return config;}
}
