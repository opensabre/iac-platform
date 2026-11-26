package io.github.opensabre.iac.integration;

import io.github.opensabre.iac.agent.AgentChatSessionService;
import io.github.opensabre.iac.agent.AgentService;
import io.github.opensabre.iac.agent.model.Agent;
import io.github.opensabre.iac.agent.model.AgentVersion;
import io.github.opensabre.iac.consumer.ConsumerApplicationService;
import io.github.opensabre.iac.rest.ExternalAgentInvocationController;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExternalAgentInvocationControllerTest {
    @Test
    void routesOnlyAuthorizedPublishedVersionsAndReturnsCorrelatedIdentifiers() {
        var applications = mock(ConsumerApplicationService.class);
        var agents = mock(AgentService.class);
        var sessions = mock(AgentChatSessionService.class);
        var endpoint = new ExternalAgentInvocationController(applications, agents, sessions);
        var token = mock(Jwt.class);
        var agent = new Agent();
        var version = new AgentVersion();
        version.setVersionNo(3);
        version.setStatus("PUBLISHED");
        agent.setStatus("DISABLED");
        when(applications.authorize(token, "agent-1", 3)).thenReturn("app-a");
        when(agents.get("agent-1")).thenReturn(agent);
        when(agents.version("agent-1", 3)).thenReturn(version);
        var request = new ExternalAgentInvocationController.Request(3, "Hello");

        assertThatThrownBy(() -> endpoint.invoke(token, "agent-1", request)).hasMessageContaining("当前不可调用");
        verifyNoInteractions(sessions);

        agent.setStatus("PUBLISHED");
        when(sessions.invokePublished(version, "app-a", "Hello")).thenReturn(
                new AgentChatSessionService.ChatResult("Hi", 42, "model-a", List.of(), "session-id", "request-id"));

        assertThat(endpoint.invoke(token, "agent-1", request)).isEqualTo(
                new ExternalAgentInvocationController.Reply("request-id", "session-id", 3, "Hi", "model-a", 42));
    }
}
