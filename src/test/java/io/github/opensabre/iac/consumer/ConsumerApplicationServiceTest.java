package io.github.opensabre.iac.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.agent.AgentService;
import io.github.opensabre.iac.governance.IacErrorType;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.shared.IacDataScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsumerApplicationServiceTest {
    private final ConsumerApplicationMapper mapper = mock(ConsumerApplicationMapper.class);
    private final ConsumerApplicationService service = new ConsumerApplicationService(mapper,
            mock(AgentService.class), mock(IacDataScope.class), new ObjectMapper());

    @Test
    void permitsOnlyExplicitlyGrantedVersionAndReservesQuota() {
        var row = registered(true);
        when(mapper.reserve(eq("reporting-app"), eq(row.getGrantsJson()), anyLong(), anyLong())).thenReturn(1);
        assertThat(service.authorize(token("reporting-app", "reporting-app", "iac.invoke"), "agent-1", 2))
                .isEqualTo("reporting-app");
        assertThatThrownBy(() -> service.authorize(token("reporting-app", "reporting-app", "iac.invoke"), "agent-1", 3))
                .isInstanceOf(IacException.class).hasMessageContaining("未获授权");
        verify(mapper, times(1)).reserve(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void rejectsAnInteractiveOrUnscopedTokenBeforeLookingUpGrants() {
        assertThatThrownBy(() -> service.authorize(token("alice", "reporting-app", "iac.invoke"), "agent-1", 2))
                .isInstanceOf(IacException.class);
        assertThatThrownBy(() -> service.authorize(token("reporting-app", "reporting-app", "read"), "agent-1", 2))
                .isInstanceOf(IacException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsDisabledApplicationAndExhaustedQuota() {
        registered(false);
        var jwt = token("reporting-app", "reporting-app", "iac.invoke");
        assertThatThrownBy(() -> service.authorize(jwt, "agent-1", 2))
                .isInstanceOf(IacException.class).hasMessageContaining("未获授权");
        registered(true);
        assertThatThrownBy(() -> service.authorize(jwt, "agent-1", 2))
                .isInstanceOfSatisfying(IacException.class, error ->
                        assertThat(error.getErrorType()).isEqualTo(IacErrorType.QUOTA_EXCEEDED));
    }

    private ConsumerApplication registered(boolean enabled) {
        var row = new ConsumerApplication();
        row.setClientId("reporting-app");
        row.setEnabled(enabled);
        row.setGrantsJson("""
                [{"agentId":"agent-1","versionNo":2}]
                """);
        when(mapper.selectById("reporting-app")).thenReturn(row);
        return row;
    }

    private static Jwt token(String subject, String audience, String scope) {
        return Jwt.withTokenValue("verified-test-token").header("alg", "RS256")
                .subject(subject).audience(List.of(audience)).claim("scope", List.of(scope)).build();
    }
}
