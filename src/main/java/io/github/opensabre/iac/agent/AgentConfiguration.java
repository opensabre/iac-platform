package io.github.opensabre.iac.agent;

import io.github.opensabre.iac.governance.IacException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned runtime configuration stored with each IAC Agent snapshot. */
public record AgentConfiguration(String schemaVersion, String mode, String systemPrompt, String primaryModel,
                                 List<Model> models, List<McpServer> mcpServers, List<Skill> skills,
                                 String primaryModelProfileId, List<String> fallbackModelProfileIds,
                                 List<String> mcpServerIds, List<String> skillIds, AssetSnapshots assetSnapshots,
                                 String ruleSetId) {
    public static final String CURRENT_SCHEMA = "3.0";
    public static final String CAPABILITY_SCHEMA = CURRENT_SCHEMA;

    /** The capability schema pins models and tools and excludes task policy. */
    public static boolean usesManagedAssets(String schema) {
        return CAPABILITY_SCHEMA.equals(schema);
    }

    /** Backward-compatible constructor for legacy callers that predate explicit execution modes. */
    public AgentConfiguration(String schemaVersion, String systemPrompt, String primaryModel,
                              List<Model> models, List<McpServer> mcpServers, List<Skill> skills,
                              String primaryModelProfileId, List<String> fallbackModelProfileIds,
                              List<String> mcpServerIds, List<String> skillIds, AssetSnapshots assetSnapshots) {
        this(schemaVersion, null, systemPrompt, primaryModel, models, mcpServers, skills,
                primaryModelProfileId, fallbackModelProfileIds, mcpServerIds, skillIds, assetSnapshots, null);
    }

    /** Provides an unconfigured draft; a managed model must be selected before validation. */
    public static AgentConfiguration defaults() {
        return new AgentConfiguration("3.0", null,
                "你是一个智能助手，请根据用户的问题准确回答。",
                null, List.of(),
                List.of(), List.of(), null, List.of(), List.of(), List.of(), null, null);
    }

    /** Validates cross-field references and bounds before a configuration enters version history. */
    public AgentConfiguration validated() {
        if (!CAPABILITY_SCHEMA.equals(schemaVersion)) throw IacException.invalidArgument("只支持纯大模型智能体配置 3.0");
        if (CAPABILITY_SCHEMA.equals(schemaVersion) && (mode != null || ruleSetId != null))
            throw IacException.invalidArgument("新版 Agent 只配置 LLM 能力，质检模式和规则集应配置在任务或业务方案中");
        if (systemPrompt == null || systemPrompt.isBlank()) throw IacException.invalidArgument("默认提示词不能为空");
        if (systemPrompt.length() > 8000) throw IacException.invalidArgument("默认提示词不能超过 8000 字符");
        if (blank(primaryModelProfileId)) throw IacException.invalidArgument("智能体必须选择主模型配置");
        ensureUnique(fallbackModelProfileIds, "备用模型");
        ensureUnique(mcpServerIds, "MCP");
        ensureUnique(skillIds, "Skill");
        if (fallbackModelProfileIds != null && fallbackModelProfileIds.contains(primaryModelProfileId))
            throw IacException.invalidArgument("主模型不能同时作为备用模型");
        return this;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void ensureUnique(List<String> values, String label) {
        if (values == null) return;
        Set<String> unique = new HashSet<>();
        for (String value : values) if (blank(value) || !unique.add(value)) throw IacException.invalidArgument(label + " 引用不能为空或重复");
    }

    public record Model(String id, String provider, String model, String endpoint, Double temperature, boolean enabled) { }
    public record McpServer(String name, String transport, String endpoint, boolean enabled) { }
    public record Skill(String name, String description, String instructions, boolean enabled) { }
    public record AssetSnapshots(ModelSnapshot primaryModel, List<ModelSnapshot> fallbackModels,
                                 List<McpSnapshot> mcpServers, List<SkillSnapshot> skills) { }
    public record ModelSnapshot(String id, String code, String name, String provider, String modelName,
                                String endpoint, String secretRef, Double temperature, Integer timeoutSeconds,
                                Integer maxRetries, Integer versionNo) { }
    public record McpSnapshot(String id, String code, String name, String transport, String endpoint,
                              String authType, String secretRef, Integer timeoutSeconds,
                              String allowedToolsJson, Integer versionNo) { }
    public record SkillSnapshot(String id, String code, String name, String description, String instructions,
                                String inputSchemaJson, String outputSchemaJson, Integer versionNo) { }

    /** Returns a copy with immutable asset details captured for approval and execution. */
    public AgentConfiguration withSnapshots(AssetSnapshots snapshots) {
        return new AgentConfiguration(schemaVersion, mode, systemPrompt, primaryModel, models, mcpServers, skills,
                primaryModelProfileId, fallbackModelProfileIds, mcpServerIds, skillIds, snapshots, ruleSetId);
    }
}
