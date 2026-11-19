package io.github.opensabre.iac.agent;

import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.mcp.dao.IacMcpServerMapper;
import io.github.opensabre.iac.modelprofile.dao.IacModelProfileMapper;
import io.github.opensabre.iac.modelprofile.model.IacModelProfile;
import io.github.opensabre.iac.skill.dao.IacSkillMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentAssetReferenceValidatorTest {
    @Test
    void capabilitySchemaPinsSkillsWithoutAgentMode() {
        IacModelProfileMapper models = mock(IacModelProfileMapper.class);
        IacSkillMapper skills = mock(IacSkillMapper.class);
        IacModelProfile model = new IacModelProfile(); model.setId("m1"); model.setStatus("ENABLED"); model.setVersionNo(4);
        var skill = new io.github.opensabre.iac.skill.model.IacSkill();
        skill.setId("s1"); skill.setStatus("ENABLED"); skill.setVersionNo(7); skill.setInstructions("fact extraction");
        when(models.selectById("m1")).thenReturn(model);
        when(skills.selectById("s1")).thenReturn(skill);
        var config = new AgentConfiguration("3.0", null, "prompt", null, null, null, null,
                "m1", List.of(), List.of(), List.of("s1"), null, null);
        var snapshot = new AgentAssetReferenceValidator(models, mock(IacMcpServerMapper.class), skills).snapshot(config);
        assertThat(snapshot.primaryModel().versionNo()).isEqualTo(4);
        assertThat(snapshot.skills()).hasSize(1);
        assertThat(snapshot.skills().getFirst().versionNo()).isEqualTo(7);
    }

    @Test
    void rejectsDisabledPrimaryModel() {
        IacModelProfileMapper models=mock(IacModelProfileMapper.class);
        IacModelProfile profile=new IacModelProfile(); profile.setStatus("DISABLED");
        when(models.selectById("model-1")).thenReturn(profile);
        AgentConfiguration config=new AgentConfiguration("3.0",null,"prompt",null,null,null,null,"model-1",List.of(),List.of(),List.of(),null,null);
        var validator=new AgentAssetReferenceValidator(models,mock(IacMcpServerMapper.class),mock(IacSkillMapper.class));
        assertThatThrownBy(()->validator.validate(config)).isInstanceOf(IacException.class).hasMessageContaining("已停用");
    }

    @Test
    void rejectsRuleOnlyWithoutResolvingAssets() {
        IacModelProfileMapper models=mock(IacModelProfileMapper.class);
        IacMcpServerMapper mcps=mock(IacMcpServerMapper.class);
        IacSkillMapper skills=mock(IacSkillMapper.class);
        AgentConfiguration config=new AgentConfiguration("2.0","RULE_ONLY","",null,null,null,null,
                null,List.of(),List.of(),List.of(),null, "rule-set-1");

        assertThatThrownBy(() -> new AgentAssetReferenceValidator(models,mcps,skills).snapshot(config))
                .isInstanceOf(IacException.class).hasMessageContaining("3.0");
        verifyNoInteractions(models,mcps,skills);
    }
}
