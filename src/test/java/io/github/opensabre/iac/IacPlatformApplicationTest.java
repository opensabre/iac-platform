package io.github.opensabre.iac;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import io.github.opensabre.governance.errorcatalog.ErrorCatalogProvider;
import io.github.opensabre.governance.audit.aspect.AuditAspect;
import io.github.opensabre.governance.dictionary.DictionaryProvider;
import io.github.opensabre.governance.registration.GovernanceRegistrationEndpoint;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
import io.github.opensabre.iac.agent.dao.AgentChatSessionMapper;
import io.github.opensabre.iac.agent.dao.AgentMapper;
import io.github.opensabre.iac.agent.dao.AgentVersionMapper;
import io.github.opensabre.iac.agent.model.Agent;
import io.github.opensabre.iac.agent.model.AgentChatSession;
import io.github.opensabre.iac.agent.model.AgentVersion;
import io.github.opensabre.iac.consumer.ConsumerApplication;
import io.github.opensabre.iac.consumer.ConsumerApplicationMapper;
import io.github.opensabre.iac.invocation.AgentInvocation;
import io.github.opensabre.iac.invocation.AgentInvocationMapper;
import io.github.opensabre.iac.invocation.InvocationHistory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.github.opensabre.security.actuator.ActuatorMonitoringTokenIssuer;
import io.github.opensabre.security.token.InternalTokenConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.config.import=optional:",
        "spring.datasource.url=jdbc:h2:mem:iac_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "opensabre.governance.dictionary.registration-enabled=false",
        "opensabre.governance.error-catalog.enabled=false",
        "opensabre.resource-registration.enabled=false",
        "opensabre.security.internal-token.enabled=true",
        "opensabre.security.internal-token.active-key-id=test-key",
        "opensabre.security.internal-token.active-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "opensabre.security.internal-token.allowed-issuers[0]=iac-platform",
        "jetcache.remote.default.type=mock",
        "jetcache.remote.longTime.type=mock",
        "jetcache.remote.shortTime.type=mock"
})
@Import(IacPlatformApplicationTest.InternalTokenNacosTestConfiguration.class)
public class IacPlatformApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ActuatorMonitoringTokenIssuer actuatorMonitoringTokenIssuer;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ConsumerApplicationMapper applications;
    @MockitoBean private AgentMapper agents;
    @MockitoBean private AgentVersionMapper versions;
    @MockitoBean private AgentChatSessionMapper sessions;
    @MockitoBean private AgentInvocationMapper invocations;
    @MockitoBean private SnapshotChatModelRouter modelRouter;

    @Test
    void loadsWithOpenSabreGovernanceAndManagedModelRouter() {
        assertThat(applicationContext.getBeansOfType(ErrorCatalogProvider.class)).isNotEmpty();
        assertThat(applicationContext.getBeansOfType(DictionaryProvider.class)).isNotEmpty();
        assertThat(applicationContext.getBeansOfType(AuditAspect.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(GovernanceRegistrationEndpoint.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SnapshotChatModelRouter.class)).hasSize(1);
    }

    @Test
    void exposesOpenApiDocumentForGatewayDiscoveryWithoutAuthentication() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void protectsManagementMetricsWithActuatorInternalToken() throws Exception {
        URI metric = URI.create("http://localhost:" + managementPort
                + "/actuator/metrics/process.uptime");
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<Void> anonymous = client.send(
                HttpRequest.newBuilder(metric).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(anonymous.statusCode()).isEqualTo(401);

        String token = actuatorMonitoringTokenIssuer.issue("iac-platform");
        HttpResponse<Void> authorized = client.send(
                HttpRequest.newBuilder(metric)
                        .header(InternalTokenConstants.HEADER, token)
                        .GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(authorized.statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsAnonymousManagementEndpoints() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        for (String path : List.of("/actuator/health", "/actuator/prometheus",
                "/actuator/opensabreGovernanceRegistration")) {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path))
                            .GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).as(path).isEqualTo(401);
        }
    }

    @Test
    void rejectsAnonymousPublicAndConsumerApis() throws Exception {
        var client = HttpClient.newHttpClient();
        for (var path : List.of("/api/iac/config/agents", "/api/iac/config/agent-sessions", "/internal/iac/agents/a")) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).as(path).isEqualTo(401);
        }
    }

    @Test
    void verifiesPublicScopeAndLinksExecutionToStoredSessionOverHttp() throws Exception {
        when(jwtDecoder.decode("application-token")).thenReturn(token("iac.invoke"));
        when(jwtDecoder.decode("unscoped-token")).thenReturn(token("read"));

        var app = new ConsumerApplication();
        app.setClientId("reporting-app");
        app.setEnabled(true);
        app.setGrantsJson("[{\"agentId\":\"agent-1\",\"versionNo\":2}]");
        when(applications.selectById("reporting-app")).thenReturn(app);
        when(applications.reserve(eq("reporting-app"), eq(app.getGrantsJson()), anyLong(), anyLong())).thenReturn(1);
        var agent = new Agent();
        agent.setStatus("PUBLISHED");
        when(agents.selectById("agent-1")).thenReturn(agent);
        var version = new AgentVersion();
        version.setAgentId("agent-1");
        version.setVersionNo(2);
        version.setStatus("PUBLISHED");
        version.setConfigJson("""
                {"schemaVersion":"3.0","systemPrompt":"Pinned prompt","primaryModelProfileId":"model",
                 "assetSnapshots":{"primaryModel":{"id":"model"},"mcpServers":[],"skills":[],"fallbackModels":[]}}
                """);
        when(versions.selectOne(any())).thenReturn(version);
        when(modelRouter.call(any(), any(), any(), any(InvocationHistory.Execution.class)))
                .thenAnswer(invocation -> invocation.<InvocationHistory.Execution>getArgument(3)
                        .step("MODEL", "test-model", () -> new ChatResponse(
                                List.of(new Generation(new AssistantMessage("Hello from the published Agent"))),
                                ChatResponseMetadata.builder().model("test-model").usage(new DefaultUsage(3, 4)).build())));

        var client = HttpClient.newHttpClient();
        var path = "/api/iac/v1/agents/agent-1/invoke";
        assertThat(client.send(invokeRequest(path, null), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(client.send(invokeRequest(path, "unscoped-token"), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        var before = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/iac/config/agent-sessions"))
                .header("Authorization", "Bearer application-token").GET().build();
        assertThat(client.send(before, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);

        var response = client.send(invokeRequest(path, "application-token"), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var reply = json.readTree(response.body()).path("data");
        assertThat(reply.path("versionNo").asInt()).isEqualTo(2);
        assertThat(reply.path("content").asText()).isEqualTo("Hello from the published Agent");
        var session = ArgumentCaptor.forClass(AgentChatSession.class);
        var execution = ArgumentCaptor.forClass(AgentInvocation.class);
        verify(sessions).insert(session.capture());
        verify(invocations).updateById(execution.capture());
        assertThat(session.getValue().getId()).isEqualTo(reply.path("sessionId").asText());
        assertThat(session.getValue().getSource()).isEqualTo("API");
        assertThat(session.getValue().getMessagesJson()).contains(reply.path("requestId").asText());
        assertThat(execution.getValue().getRequestId()).isEqualTo(reply.path("requestId").asText());
        assertThat(execution.getValue().getSessionId()).isEqualTo(session.getValue().getId());
        assertThat(execution.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(execution.getValue().getInputTokens()).isEqualTo(3);
        assertThat(execution.getValue().getStepsJson()).contains("test-model").doesNotContain("Hello");
    }

    private HttpRequest invokeRequest(String path, String bearer) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("""
                        {"versionNo":2,"input":"Hello"}
                        """));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return builder.build();
    }

    private static Jwt token(String scope) {
        return Jwt.withTokenValue("test").header("alg", "RS256").subject("reporting-app")
                .audience(List.of("reporting-app")).claim("scope", List.of(scope)).claim("roles", List.of()).build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class InternalTokenNacosTestConfiguration {

        @Bean
        NacosConfigManager nacosConfigManager() {
            ConfigService configService = mock(ConfigService.class);
            NacosConfigManager manager = mock(NacosConfigManager.class);
            when(manager.getConfigService()).thenReturn(configService);
            return manager;
        }
    }
}
