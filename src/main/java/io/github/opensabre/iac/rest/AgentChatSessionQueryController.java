package io.github.opensabre.iac.rest;

import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.iac.agent.AgentChatSessionService;
import io.github.opensabre.iac.invocation.InvocationHistory;
import io.github.opensabre.iac.shared.IacPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

/** Cross-agent read-only queries reuse the session store and its existing data-scope checks. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/iac/config/agent-sessions")
@Tag(name = "智能体会话管理")
public class AgentChatSessionQueryController {
    private final AgentChatSessionService service;
    private final InvocationHistory invocations;

    @GetMapping
    @Operation(summary = "跨智能体分页查询会话历史")
    @ResourcePermission(code="iac:agent-session:query", name="查询智能体会话", type="iac", description="跨智能体查询会话历史")
    public IacPage<AgentChatSessionService.Summary> page(@RequestParam(required=false) String agentId,
            @RequestParam(defaultValue="1") long current, @RequestParam(defaultValue="10") long size,
            @RequestParam(required=false) String title, @RequestParam(required=false) String owner,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedTo,
            @RequestParam(required=false) String sessionId, @RequestParam(required=false) Integer versionNo,
            @RequestParam(required=false) String source, @RequestParam(required=false) String caller,
            @RequestParam(required=false) String executionStatus) {
        return service.page(agentId, current, size, title, owner, startedFrom, startedTo,
                new AgentChatSessionService.Filters(sessionId, versionNo, source, caller, executionStatus));
    }
    @GetMapping("/executions")
    @Operation(summary = "按请求 ID 或任务标识查找执行明细")
    @ResourcePermission(code="iac:agent-session:execution-query", name="查询智能体执行明细", type="iac",
            description="按请求 ID 或任务标识查找无会话的执行明细")
    public IacPage<InvocationHistory.Detail> executions(@RequestParam(required=false) String requestId,
            @RequestParam(required=false) String businessReference,
            @RequestParam(defaultValue="1") long current, @RequestParam(defaultValue="10") long size) {
        return invocations.lookup(requestId, businessReference, current, size);
    }
}
