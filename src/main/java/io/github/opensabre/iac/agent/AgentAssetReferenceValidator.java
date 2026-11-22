package io.github.opensabre.iac.agent;

import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.mcp.dao.IacMcpServerMapper;
import io.github.opensabre.iac.modelprofile.dao.IacModelProfileMapper;
import io.github.opensabre.iac.skill.dao.IacSkillMapper;
import org.springframework.stereotype.Component;
import java.util.LinkedHashSet;
import java.util.List;
import static io.github.opensabre.iac.agent.AgentConfiguration.*;

/** Validates that new Agent configurations only reference enabled managed assets. */
@Component
public class AgentAssetReferenceValidator {
    private final IacModelProfileMapper models; private final IacMcpServerMapper mcps; private final IacSkillMapper skills;
    public AgentAssetReferenceValidator(IacModelProfileMapper models, IacMcpServerMapper mcps, IacSkillMapper skills) { this.models=models; this.mcps=mcps; this.skills=skills; }
    public void validate(AgentConfiguration config) {
        config.validated();
        requireEnabledModel(config.primaryModelProfileId());
        distinct(config.fallbackModelProfileIds()).forEach(this::requireEnabledModel);
        distinct(config.mcpServerIds()).forEach(id -> { var item=mcps.selectById(id); if(item==null||!"ENABLED".equals(item.getStatus())) throw IacException.invalidState("MCP 不存在或已停用: "+id); });
        distinct(config.skillIds()).forEach(id -> { var item=skills.selectById(id); if(item==null||!"ENABLED".equals(item.getStatus())) throw IacException.invalidState("Skill 不存在或已停用: "+id); });
    }
    /** Resolves enabled references into a secret-free immutable Agent asset snapshot. */
    public AssetSnapshots snapshot(AgentConfiguration config) {
        validate(config);
        return new AssetSnapshots(model(config.primaryModelProfileId()), distinct(config.fallbackModelProfileIds()).stream().map(this::model).toList(),
                distinct(config.mcpServerIds()).stream().map(id -> { var x=mcps.selectById(id); return new McpSnapshot(x.getId(),x.getCode(),x.getName(),x.getTransport(),x.getEndpoint(),x.getAuthType(),x.getSecretRef(),x.getTimeoutSeconds(),x.getAllowedToolsJson(),x.getVersionNo()); }).toList(),
                distinct(config.skillIds()).stream().map(id -> { var x=skills.selectById(id); return new SkillSnapshot(x.getId(),x.getCode(),x.getName(),x.getDescription(),x.getInstructions(),x.getInputSchemaJson(),x.getOutputSchemaJson(),x.getVersionNo()); }).toList());
    }
    private ModelSnapshot model(String id) { var x=models.selectById(id); return new ModelSnapshot(x.getId(),x.getCode(),x.getName(),x.getProvider(),x.getModelName(),x.getEndpoint(),x.getSecretRef(),x.getTemperature(),x.getTimeoutSeconds(),x.getMaxRetries(),x.getVersionNo()); }
    private void requireEnabledModel(String id) { var item=models.selectById(id); if(item==null||!"ENABLED".equals(item.getStatus())) throw IacException.invalidState("模型配置不存在或已停用: "+id); }
    private static List<String> distinct(List<String> ids) { return ids==null ? List.of() : List.copyOf(new LinkedHashSet<>(ids)); }
}
