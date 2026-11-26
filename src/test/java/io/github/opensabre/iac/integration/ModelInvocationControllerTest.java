package io.github.opensabre.iac.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.invocation.InvocationHistory;
import io.github.opensabre.iac.shared.IacDataScope;
import org.junit.jupiter.api.BeforeEach;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
import io.github.opensabre.iac.result.llm.SnapshotMcpToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Exercises the internal capability contract without live models or shared service changes. */
class ModelInvocationControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ConsumerAccess access = mock(ConsumerAccess.class);
    private final SnapshotChatModelRouter models = mock(SnapshotChatModelRouter.class);
    private final SnapshotMcpToolProvider tools = mock(SnapshotMcpToolProvider.class);
    private final InvocationHistory history = mock(InvocationHistory.class);
    private final InvocationHistory.Execution execution = mock(InvocationHistory.Execution.class);
    private final ModelInvocationController controller = new ModelInvocationController(access, models, tools, json,
            history, mock(IacDataScope.class));

    @BeforeEach
    void configureHistory() {
        when(access.requireConsumer()).thenReturn("iqc-platform");
        when(history.start(eq("INTERNAL"), eq("iqc-platform"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(execution);
        when(execution.requestId()).thenReturn("request-1");
    }
    private ModelInvocationController.Request request() throws Exception {
        return new ModelInvocationController.Request(json.readTree("""
                {"configJson":{"schemaVersion":"3.0","assetSnapshots":{"primaryModel":{}}},
                 "taskOverrides":{"temperature":0,"timeoutSeconds":1}}
                """),List.of(new ModelInvocationController.Turn("system","instructions"),
                new ModelInvocationController.Turn("user","question")),false);
    }
    @Test void preservesMessagesOverridesAndActualUsage() throws Exception {
        var request = request();
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))),
                ChatResponseMetadata.builder().model("provider-model").usage(new DefaultUsage(0,17)).build());
        when(models.call(any(Prompt.class),same(request.snapshot()), any(), same(execution))).thenReturn(response);
        var reply = controller.invoke(request);
        assertThat(reply.content()).isEqualTo("answer");
        assertThat(reply.model()).isEqualTo("provider-model");
        assertThat(reply.inputTokens()).isZero(); assertThat(reply.outputTokens()).isEqualTo(17);
        assertThat(reply.requestId()).isEqualTo("request-1");
        verify(execution).succeeded(response);
        verify(access).requireConsumer();
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(models).call(prompt.capture(),same(request.snapshot()), any(), same(execution));
        assertThat(prompt.getValue().getInstructions()).extracting(message -> message.getText())
                .containsExactly("instructions","question");
        assertThat(request.snapshot().path("taskOverrides").path("temperature").asDouble()).isZero();
        verifyNoInteractions(tools);
    }
    @Test void absentUsageStaysUnknown() throws Exception {
        when(models.call(any(Prompt.class), any(), any(), same(execution))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("answer")))));
        var reply = controller.invoke(request());
        assertThat(reply.inputTokens()).isNull(); assertThat(reply.outputTokens()).isNull();
    }
    @Test void rejectsUntrustedRequestsBeforeModelOrMcpAccess() throws Exception {
        when(access.requireConsumer()).thenThrow(new IllegalStateException("untrusted"));
        assertThatThrownBy(() -> controller.invoke(request())).hasMessage("untrusted");
        verifyNoInteractions(models,tools);
    }
    @Test void rejectsMissingSnapshotsLegacySchemasAndUnsupportedRoles() throws Exception {
        var request = request();
        ((com.fasterxml.jackson.databind.node.ObjectNode)request.snapshot().path("configJson")).put("schemaVersion","2.0");
        assertThatThrownBy(() -> controller.invoke(request)).hasMessageContaining("已冻结");
        var valid = request();
        assertThatThrownBy(() -> controller.invoke(new ModelInvocationController.Request(valid.snapshot(),
                List.of(new ModelInvocationController.Turn("tool","untrusted")),false))).hasMessageContaining("消息角色");
        verifyNoInteractions(models,tools);
    }
}
