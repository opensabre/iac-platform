package io.github.opensabre.iac.result.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Verifies task controls at the actual SDK HTTP boundary, including timeout-driven fallback. */
class TaskModelHttpParametersTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsTaskPromptAndExplicitZeroToPrimaryAndFallbackWithoutChangingPublishedSnapshot() throws Exception {
        try (var endpoint = new Endpoint(true, false)) {
            var snapshot = snapshot(endpoint);
            String frozen = snapshot.toString();
            var result = new SnapshotChatModelRouter(mapper, reference -> "isolated-test-key").call(new org.springframework.ai.chat.prompt.Prompt(
                    "已发布行为\n本次退款要求\n申请退款"), snapshot);
            assertThat(mapper.readTree(result.getResult().getOutput().getText()).path("reason").asText())
                    .isEqualTo("fallback reply");
            assertThat(endpoint.requests).extracting(Request::path).containsExactly("/primary/v1/chat/completions", "/fallback/v1/chat/completions");
            for (var request : endpoint.requests) {
                assertThat(request.body.path("temperature").isNumber()).isTrue();
                assertThat(request.body.path("temperature").asDouble()).isZero();
                assertThat(request.body.path("messages").toString()).contains("已发布行为", "本次退款要求", "申请退款");
            }
            assertThat(endpoint.requests).extracting(request -> request.body.path("model").asText()).containsExactly("primary-model", "fallback-model");
            assertThat(snapshot.toString()).isEqualTo(frozen);
        }
    }

    @Test
    void taskTransportTimeoutMovesToFallbackInsteadOfAcceptingLatePrimaryReply() throws Exception {
        try (var endpoint = new Endpoint(false, true)) {
            var result = new SnapshotChatModelRouter(mapper, reference -> "isolated-test-key").call(new org.springframework.ai.chat.prompt.Prompt(
                    "已发布行为\n本次退款要求\n申请退款"), snapshot(endpoint));
            assertThat(mapper.readTree(result.getResult().getOutput().getText()).path("reason").asText())
                    .isEqualTo("fallback reply");
            assertThat(endpoint.requests).extracting(Request::path).containsExactly("/primary/v1/chat/completions", "/fallback/v1/chat/completions");
        }
    }

    private ObjectNode snapshot(Endpoint endpoint) {
        var snapshot = mapper.createObjectNode();
        var config = snapshot.putObject("configJson").put("schemaVersion", "3.0").put("systemPrompt", "已发布行为");
        var assets = config.putObject("assetSnapshots");
        assets.set("primaryModel", model(endpoint.url() + "/primary/v1", "primary-model"));
        assets.putArray("fallbackModels").add(model(endpoint.url() + "/fallback/v1", "fallback-model"));
        snapshot.putObject("taskOverrides").put("prompt", "本次退款要求").put("temperature", 0).put("timeoutSeconds", 1);
        return snapshot;
    }

    private ObjectNode model(String endpoint, String name) {
        return mapper.createObjectNode().put("provider", "OPENAI").put("endpoint", endpoint).put("modelName", name)
                .put("secretRef", "test-reference").put("temperature", 0.8).put("timeoutSeconds", 30).put("maxRetries", 0);
    }

    private record Request(String path, JsonNode body) { }

    /** Test-only model endpoint; never uses a live provider or application credentials. */
    private final class Endpoint implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        private final List<Request> requests = new CopyOnWriteArrayList<>();

        private Endpoint(boolean failPrimary, boolean delayPrimary) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    requests.add(new Request(path, mapper.readTree(exchange.getRequestBody())));
                    boolean primary = path.contains("/primary/");
                    if (primary && delayPrimary) Thread.sleep(3000);
                    String reason = primary ? "primary reply" : "fallback reply";
                    String content = mapper.writeValueAsString(mapper.createObjectNode().put("hit", true).put("reason", reason));
                    var response = mapper.createObjectNode().put("id", "test-completion").put("object", "chat.completion").put("created", 1).put("model", "test-model");
                    var choice = response.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop");
                    choice.putObject("message").put("role", "assistant").put("content", content);
                    byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(primary && failPrimary ? 503 : 200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally { exchange.close(); }
            });
            server.start();
        }
        private String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
