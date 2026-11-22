package io.github.opensabre.iac.rest;
import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.governance.audit.annotations.*;
import io.github.opensabre.governance.ratelimit.annotations.RateLimit;
import io.github.opensabre.iac.agent.*;
import io.github.opensabre.iac.agent.model.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** Owns agent management independently of inspection rules and results. */
@RestController
@RequestMapping("/api/iac/config")
@lombok.RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;
    @GetMapping("/agents")
    @ResourcePermission(code = "iac:agent:list", name = "查看 Agent", type = "iac", description = "查询 Agent")
    public List<Agent> agents() { return agentService.list(); }

    @PostMapping("/agents")
    @ResourcePermission(code = "iac:agent:create", name = "管理 Agent", type = "iac", description = "创建 Agent")
    @Audit(operationType = OperationType.CREATE, description = "创建 IAC Agent", module = "IAC_AGENT")
    @RateLimit(sceneCode = "iac-agent-create", maxCount = 20, period = 60)
    public Agent createAgent(@RequestBody AgentRequest request) { return agentService.create(request.name(), request.code(), request.description(), request.configJson()); }

    @PostMapping("/agents/{id}/submit")
    @ResourcePermission(code = "iac:agent:submit", name = "提交 Agent 审批", type = "iac", description = "提交 Agent 审批")
    @Audit(operationType = OperationType.UPDATE, description = "提交 IAC Agent 审批", module = "IAC_AGENT")
    public Agent submitAgent(@PathVariable String id) { return agentService.submit(id); }

    @PostMapping("/agents/{id}/approve")
    @ResourcePermission(code = "iac:agent:approve", name = "审批 Agent", type = "iac", description = "审批通过 Agent")
    @Audit(operationType = OperationType.UPDATE, description = "审批通过 IAC Agent", module = "IAC_AGENT")
    public Agent publishAgent(@PathVariable String id) { return agentService.publish(id); }

    @PostMapping("/agents/{id}/reject")
    @ResourcePermission(code = "iac:agent:reject", name = "驳回 Agent", type = "iac", description = "驳回 Agent")
    @Audit(operationType = OperationType.UPDATE, description = "驳回 IAC Agent", module = "IAC_AGENT")
    public Agent rejectAgent(@PathVariable String id) { return agentService.reject(id); }

    @PostMapping("/agents/{id}/disable")
    @ResourcePermission(code = "iac:agent:disable", name = "停用 Agent", type = "iac", description = "停用已发布 Agent")
    @Audit(operationType = OperationType.UPDATE, description = "停用 IAC Agent", module = "IAC_AGENT")
    public Agent disableAgent(@PathVariable String id) { return agentService.disable(id); }

    @GetMapping("/agents/{id}/versions")
    @ResourcePermission(code = "iac:agent:version:list", name = "查看 Agent 版本", type = "iac", description = "查询 Agent 版本")
    public List<AgentVersion> agentVersions(@PathVariable String id) { return agentService.versions(id); }

    @PostMapping("/agents/{id}/versions")
    @ResourcePermission(code = "iac:agent:version:create", name = "创建 Agent 版本", type = "iac", description = "创建 Agent 版本")
    @Audit(operationType = OperationType.CREATE, description = "创建 IAC Agent 版本", module = "IAC_AGENT")
    public AgentVersion createAgentVersion(@PathVariable String id, @RequestBody AgentRequest request) { return agentService.createVersion(id, request.name(), request.code(), request.description(), request.configJson()); }

    @PostMapping("/agents/{id}/versions/{versionNo}/rollback")
    @ResourcePermission(code = "iac:agent:version:rollback", name = "回滚 Agent 版本", type = "iac", description = "基于历史版本创建新草稿")
    @Audit(operationType = OperationType.UPDATE, description = "回滚 IAC Agent 版本", module = "IAC_AGENT")
    public AgentVersion rollbackAgent(@PathVariable String id, @PathVariable int versionNo) { return agentService.rollback(id, versionNo); }

    @GetMapping("/agents/{id}/versions/compare")
    @ResourcePermission(code = "iac:agent:version:compare", name = "比较 Agent 版本", type = "iac", description = "比较 Agent 版本配置差异")
    public AgentService.AgentVersionComparison compareAgentVersions(@PathVariable String id,
            @RequestParam int fromVersion, @RequestParam int toVersion) {
        return agentService.compare(id, fromVersion, toVersion);
    }

    public record AgentRequest(String name,String code,String description,String configJson) { }
}
