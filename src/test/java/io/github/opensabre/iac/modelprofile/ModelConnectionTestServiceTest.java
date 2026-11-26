package io.github.opensabre.iac.modelprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.errors.OpenAIServiceException;
import io.github.opensabre.iac.result.llm.SecretReferenceException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.opensabre.iac.modelprofile.dao.IacModelProfileMapper;
import io.github.opensabre.iac.modelprofile.model.IacModelProfile;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelConnectionTestServiceTest {

    @Test
    void reportsSuccessWithoutReturningCredentials() {
        IacModelProfileMapper mapper = mock(IacModelProfileMapper.class);
        SnapshotChatModelRouter router = mock(SnapshotChatModelRouter.class);
        ChatModel model = mock(ChatModel.class);
        IacModelProfile profile = new IacModelProfile();
        profile.setProvider("OPENAI");
        profile.setModelName("gpt-test");
        profile.setSecretRef("env:IAC_MODEL_API_KEY");
        when(mapper.selectById("model-1")).thenReturn(profile);
        when(router.create(any())).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

        var result = new ModelConnectionTestService(mapper, router, new ObjectMapper()).test("model-1");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).doesNotContain("IAC_MODEL_API_KEY");
    }

    @Test
    void redactsProviderFailureDetails() {
        IacModelProfileMapper mapper = mock(IacModelProfileMapper.class);
        SnapshotChatModelRouter router = mock(SnapshotChatModelRouter.class);
        IacModelProfile profile = new IacModelProfile();
        profile.setProvider("OPENAI");
        profile.setModelName("gpt-test");
        when(mapper.selectById("model-1")).thenReturn(profile);
        when(router.create(any())).thenThrow(new IllegalStateException("Authorization: Bearer leaked-secret"));

        var result = new ModelConnectionTestService(mapper, router, new ObjectMapper()).test("model-1");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("leaked-secret").doesNotContain("Authorization");
    }
    @ParameterizedTest
    @CsvSource({
        "401, AUTHENTICATION_FAILED", "403, AUTHENTICATION_FAILED",
        "404, MODEL_UNAVAILABLE", "429, RATE_LIMITED", "504, TIMEOUT",
        "500, CONNECTION_FAILED"
    })
    void classifiesProviderStatus(int status, String expectedCode) {
        OpenAIServiceException remote = mock(OpenAIServiceException.class);
        when(remote.statusCode()).thenReturn(status);
        assertDiagnostic(new IllegalStateException("provider details", remote), expectedCode);
    }

    @Test
    void classifiesMissingCredentialsAndTimeout() {
        assertDiagnostic(new SecretReferenceException(), "CREDENTIAL_UNAVAILABLE");
        assertDiagnostic(new IllegalStateException(new SocketTimeoutException()), "TIMEOUT");
    }

    private void assertDiagnostic(RuntimeException failure, String expectedCode) {
        var mapper = mock(IacModelProfileMapper.class);
        var router = mock(SnapshotChatModelRouter.class);
        when(mapper.selectById("model-1")).thenReturn(new IacModelProfile());
        when(router.create(any())).thenThrow(failure);

        var result = new ModelConnectionTestService(mapper, router, new ObjectMapper()).test("model-1");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(expectedCode);
    }
}
