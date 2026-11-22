package io.github.opensabre.iac.rest;

import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.iac.agent.AgentChatTestService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iac/config/agents")
public class AgentChatTestController {
    private final io.github.opensabre.iac.agent.AgentChatSessionService service;
    public AgentChatTestController(io.github.opensabre.iac.agent.AgentChatSessionService service) { this.service=service; }
    // Reuse existing capability-management authorization; no shared-service permission changes.
    @io.github.opensabre.governance.ratelimit.annotations.RateLimit(sceneCode="iac-agent-test-chat", maxCount=20, period=60)
    @PostMapping("/test-chat")
    @ResourcePermission(code="iac:agent:chat", name="管理智能体", type="iac", description="测试当前智能体的大模型能力")
    public io.github.opensabre.iac.agent.AgentChatSessionService.ChatResult chat(@RequestBody AgentChatTestService.Request request) { return service.chat(request); }
}
