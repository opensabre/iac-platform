package io.github.opensabre.iac.rest;

import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.github.opensabre.governance.ratelimit.annotations.RateLimit;
import io.github.opensabre.iac.skill.IacSkillService;
import io.github.opensabre.iac.skill.model.IacSkill;
import io.github.opensabre.iac.skill.model.IacSkillVersion;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP management API for reusable IAC Skills. */
@RestController
@RequestMapping("/api/iac/agent-assets/skills")
public class IacSkillController {
    private final IacSkillService service;
    public IacSkillController(IacSkillService service) { this.service = service; }

    @GetMapping
    @ResourcePermission(code = "iac:skill:list", name = "查看 Skill", type = "iac", description = "查询 Agent Skill 资产")
    public List<IacSkill> list() { return service.list(); }

    @PostMapping
    @ResourcePermission(code = "iac:skill:create", name = "管理 Skill", type = "iac", description = "创建 Agent Skill 资产")
    @Audit(operationType = OperationType.CREATE, description = "创建 IAC Skill", module = "IAC_SKILL")
    @RateLimit(sceneCode = "iac-skill-create", maxCount = 20, period = 60)
    public IacSkill create(@RequestBody SkillRequest request) { return service.create(request.name(), request.code(), request.description(), request.instructions(), request.inputSchemaJson(), request.outputSchemaJson()); }

    @PutMapping("/{id}")
    @ResourcePermission(code = "iac:skill:update", name = "管理 Skill", type = "iac", description = "修改 Agent Skill 资产")
    @Audit(operationType = OperationType.UPDATE, description = "修改 IAC Skill", module = "IAC_SKILL")
    public IacSkill update(@PathVariable String id, @RequestBody SkillRequest request) { return service.update(id, request.name(), request.description(), request.instructions(), request.inputSchemaJson(), request.outputSchemaJson()); }

    @PostMapping("/{id}/enable")
    @ResourcePermission(code = "iac:skill:enable", name = "管理 Skill", type = "iac", description = "启用 Agent Skill 资产")
    @Audit(operationType = OperationType.UPDATE, description = "启用 IAC Skill", module = "IAC_SKILL")
    public IacSkill enable(@PathVariable String id) { return service.setEnabled(id, true); }

    @PostMapping("/{id}/disable")
    @ResourcePermission(code = "iac:skill:disable", name = "管理 Skill", type = "iac", description = "停用 Agent Skill 资产")
    @Audit(operationType = OperationType.UPDATE, description = "停用 IAC Skill", module = "IAC_SKILL")
    public IacSkill disable(@PathVariable String id) { return service.setEnabled(id, false); }

    @GetMapping("/{id}/versions")
    @ResourcePermission(code = "iac:skill:versions", name = "查看 Skill 版本", type = "iac", description = "查询 Skill 历史版本")
    public List<IacSkillVersion> versions(@PathVariable String id) { return service.versions(id); }

    @PostMapping("/{id}/versions/{versionNo}/rollback")
    @ResourcePermission(code = "iac:skill:rollback", name = "管理 Skill", type = "iac", description = "回滚 Skill 历史版本")
    @Audit(operationType = OperationType.UPDATE, description = "回滚 IAC Skill", module = "IAC_SKILL")
    public IacSkill rollback(@PathVariable String id, @PathVariable int versionNo) { return service.rollback(id, versionNo); }

    public record SkillRequest(String name, String code, String description, String instructions,
                               String inputSchemaJson, String outputSchemaJson) { }
}
