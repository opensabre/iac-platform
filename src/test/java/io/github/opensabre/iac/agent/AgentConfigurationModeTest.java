package io.github.opensabre.iac.agent;

import io.github.opensabre.iac.governance.IacException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConfigurationModeTest {
    @Test
    void capabilityAgentHasNoTaskModeOrRuleSet() {
        assertThatCode(() -> capability(null, null).validated()).doesNotThrowAnyException();
        assertThatThrownBy(() -> capability("RULE_ONLY", null).validated()).hasMessageContaining("只配置 LLM 能力");
        assertThatThrownBy(() -> capability(null, "set-1").validated()).hasMessageContaining("只配置 LLM 能力");
    }

    @Test
    void capabilityAgentStillRequiresModelAndPrompt() {
        var config = new AgentConfiguration("3.0", null, "prompt", null, null, null, null,
                null, List.of(), List.of(), List.of(), null, null);
        assertThatThrownBy(config::validated).hasMessageContaining("主模型");
    }

    private AgentConfiguration capability(String mode, String setId) {
        return new AgentConfiguration("3.0", mode, "prompt", null, null, null, null,
                "model-1", List.of(), List.of("mcp-1"), List.of("skill-1"), null, setId);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RULE_ONLY", "RULE_THEN_LLM", "AGENT_LLM", "LLM_THEN_RULE"})
    void rejectsHistoricalTaskModes(String mode) {
        assertThatThrownBy(() -> config(mode, "prompt", "model-1").validated())
                .isInstanceOf(IacException.class).hasMessageContaining("3.0");
    }

    private AgentConfiguration config(String mode, String prompt, String modelId) {
        return new AgentConfiguration("2.0", mode, prompt, null, null, null, null,
                modelId, List.of(), List.of(), List.of(), null,
                "RULE_ONLY".equals(mode) ? "rule-set-1" : null);
    }
}
