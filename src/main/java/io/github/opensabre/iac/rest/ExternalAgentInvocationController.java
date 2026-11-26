package io.github.opensabre.iac.rest;

import io.github.opensabre.iac.agent.AgentChatSessionService;
import io.github.opensabre.iac.agent.AgentService;
import io.github.opensabre.iac.consumer.ConsumerApplicationService;
import io.github.opensabre.common.core.entity.vo.Result;
import io.github.opensabre.iac.governance.IacErrorType;
import io.github.opensabre.iac.governance.IacException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Application-facing synchronous invocation of explicitly authorized, published Agent versions. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/iac/v1/agents")
@Tag(name = "智能体开放调用")
public class ExternalAgentInvocationController {
    private final ConsumerApplicationService applications;
    private final AgentService agents;
    private final AgentChatSessionService sessions;

    @PostMapping("/{agentId}/invoke")
    @Operation(summary = "同步调用已发布智能体版本", description = "使用 OAuth client_credentials 获取带 iac.invoke scope 的访问令牌")
    public Reply invoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String agentId, @RequestBody Request request) {
        if (request == null || request.versionNo() == null || request.versionNo() < 1 || request.input() == null
                || request.input().isBlank() || request.input().length() > 32_000)
            throw IacException.invalidArgument("请指定版本，并提供不超过 32000 字符的输入");
        String caller = applications.authorize(jwt, agentId, request.versionNo());
        var agent = agents.get(agentId);
        var version = agents.version(agentId, request.versionNo());
        if (!"PUBLISHED".equals(agent.getStatus()) || !"PUBLISHED".equals(version.getStatus()))
            throw IacException.invalidState("智能体或版本当前不可调用");
        var result = sessions.invokePublished(version, caller, request.input());
        return new Reply(result.requestId(), result.sessionId(), version.getVersionNo(), result.content(),
                result.model(), result.latencyMillis());
    }

    /** Public callers receive HTTP statuses and the same OpenSabre error codes as the management APIs. */
    @ExceptionHandler(IacException.class)
    ResponseEntity<Result<?>> domainError(IacException exception) {
        var type = (IacErrorType) exception.getErrorType();
        var status = switch (type) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_STATE -> HttpStatus.CONFLICT;
            case QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return ResponseEntity.status(status).body(Result.fail(type, exception.getMessage()));
    }

    /** An unsuccessful execution stays correlatable without returning any provider error payload. */
    @ExceptionHandler(AgentChatSessionService.PublishedInvocationFailure.class)
    ResponseEntity<Result<?>> executionError(AgentChatSessionService.PublishedInvocationFailure failure) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .header("X-IAC-Request-Id", failure.requestId())
                .body(Result.fail(IacErrorType.INVALID_STATE, failure.getMessage()));
    }

    public record Request(Integer versionNo, String input) { }
    public record Reply(String requestId, String sessionId, int versionNo, String content, String model, long latencyMillis) { }
}
