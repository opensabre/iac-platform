package io.github.opensabre.iac.invocation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.result.llm.ModelFailure;
import io.github.opensabre.iac.shared.IacDataScope;
import io.github.opensabre.iac.shared.IacPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Supplier;

/** Creates and updates an invocation without holding a database transaction during remote execution. */
@Service
@RequiredArgsConstructor
public class InvocationHistory {
    private final AgentInvocationMapper mapper;
    private final ObjectMapper json;
    private final IacDataScope scope;

    /** Persists the request before execution so interrupted requests can be identified by their ID. */
    public Execution start(String source, String caller, String agentId, Integer versionNo,
                           String sessionId, String businessReference, String owner, String ownerGroupId) {
        var row = new AgentInvocation();
        row.setRequestId(UUID.randomUUID().toString());
        row.setSource(source);
        row.setCaller(caller);
        row.setAgentId(agentId);
        row.setVersionNo(versionNo);
        row.setSessionId(sessionId);
        row.setBusinessReference(businessReference);
        row.setOwner(owner);
        row.setOwnerGroupId(ownerGroupId);
        row.setStatus("RUNNING");
        row.setStartedTime(LocalDateTime.now());
        mapper.insert(row);
        return new Execution(row);
    }

    /** Loads the latest execution details attached to a session after its data scope has been verified. */
    public List<Detail> forSession(String sessionId, List<String> messageRequestIds) {
        var query = Wrappers.<AgentInvocation>lambdaQuery()
                .eq(AgentInvocation::getSessionId, sessionId)
                .orderByDesc(AgentInvocation::getStartedTime).orderByDesc(AgentInvocation::getRequestId);
        var executions = new LinkedHashMap<String, AgentInvocation>();
        for (var row : mapper.selectPage(new Page<>(1, 100, false), query).getRecords())
            executions.put(row.getRequestId(), row);
        var missing = messageRequestIds.stream().distinct().filter(id -> !executions.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            var linked = Wrappers.<AgentInvocation>lambdaQuery()
                    .eq(AgentInvocation::getSessionId, sessionId).in(AgentInvocation::getRequestId, missing);
            for (var row : mapper.selectList(linked)) executions.put(row.getRequestId(), row);
        }
        return executions.values().stream().map(this::detail).toList();
    }

    /** Looks up an execution by an exact request ID or business reference within the current data scope. */
    public IacPage<Detail> lookup(String requestId, String businessReference, long current, long size) {
        boolean byRequest = requestId != null && !requestId.isBlank();
        boolean byBusiness = businessReference != null && !businessReference.isBlank();
        if (!byRequest && !byBusiness) throw IacException.invalidArgument("请输入请求 ID 或任务标识");
        var query = Wrappers.<AgentInvocation>lambdaQuery()
                .eq(byRequest, AgentInvocation::getRequestId, requestId)
                .eq(byBusiness, AgentInvocation::getBusinessReference, businessReference)
                .orderByDesc(AgentInvocation::getStartedTime).orderByDesc(AgentInvocation::getRequestId);
        if (!scope.canViewAll()) {
            String group = scope.groupId();
            query.and(q -> q.eq(AgentInvocation::getOwner, scope.owner())
                    .or(group != null, nested -> nested.eq(AgentInvocation::getOwnerGroupId, group)));
        }
        var page = mapper.selectPage(new Page<>(Math.max(1, current), Math.min(100, Math.max(1, size))), query);
        return new IacPage<>(page.getRecords().stream().map(this::detail).toList(),
                page.getCurrent(), page.getSize(), page.getTotal());
    }

    private Detail detail(AgentInvocation row) {
        List<Step> steps;
        try {
            steps = row.getStepsJson() == null ? List.of() : json.readValue(row.getStepsJson(), new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw IacException.invalidState("无法读取调用步骤");
        }
        return new Detail(row.getRequestId(), row.getSource(), row.getCaller(), row.getAgentId(), row.getVersionNo(),
                row.getSessionId(), row.getBusinessReference(), row.getStatus(), row.getErrorCode(), row.getModelName(),
                row.getDurationMs(), row.getInputTokens(), row.getOutputTokens(), row.getStartedTime(), steps);
    }

    /** Request-local measurements. A step records only its resource name and outcome. */
    public final class Execution {
        private final AgentInvocation row;
        private final long startNanos = System.nanoTime();
        private final List<Step> steps = new ArrayList<>();

        private Execution(AgentInvocation row) {
            this.row = row;
        }

        public String requestId() {
            return row.getRequestId();
        }

        public <T> T step(String type, String name, Supplier<T> action) {
            long start = System.nanoTime();
            String status = "FAILED";
            String errorCode = null;
            try {
                T result = action.get();
                status = "SUCCEEDED";
                return result;
            } catch (RuntimeException failure) {
                errorCode = switch (type) {
                    case "MODEL" -> ModelFailure.from(failure).name();
                    case "MCP" -> "MCP_CONNECTION_FAILED";
                    default -> "TOOL_CALL_FAILED";
                };
                throw failure;
            } finally {
                synchronized (steps) {
                    if (steps.size() < 200) steps.add(new Step(type, name, status, errorCode, (start - startNanos) / 1_000_000, elapsedMillis(start)));
                }
            }
        }

        public void succeeded(ChatResponse response) {
            var metadata = response.getMetadata();
            if (metadata != null) {
                String model = metadata.getModel();
                if (model != null) row.setModelName(model.substring(0, model.offsetByCodePoints(0,
                        Math.min(255, model.codePointCount(0, model.length())))));
                var usage = metadata.getUsage();
                if (usage != null && !(usage instanceof EmptyUsage)) {
                    row.setInputTokens(usage.getPromptTokens());
                    row.setOutputTokens(usage.getCompletionTokens());
                }
            }
            complete("SUCCEEDED");
        }

        /** Preserves the most specific observed failure code without exposing exception text. */
        public String failureCode(String fallback) {
            synchronized (steps) {
                return steps.stream().filter(step -> step.errorCode() != null)
                        .max(Comparator.comparingLong(Step::startedAfterMs)).map(Step::errorCode).orElse(fallback);
            }
        }

        public void failed(String errorCode) {
            row.setErrorCode(errorCode);
            complete("FAILED");
        }

        private void complete(String status) {
            row.setStatus(status);
            row.setDurationMs(elapsedMillis(startNanos));
            try {
                synchronized (steps) {
                    row.setStepsJson(json.writeValueAsString(steps.stream().sorted(Comparator.comparingLong(Step::startedAfterMs)).toList()));
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("无法保存调用步骤", exception);
            }
            mapper.updateById(row);
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    public record Detail(String requestId, String source, String caller, String agentId, Integer versionNo,
                         String sessionId, String businessReference, String status, String errorCode, String modelName,
                         Long durationMs, Integer inputTokens, Integer outputTokens, LocalDateTime startedTime,
                         List<Step> steps) { }

    public record Step(String type, String name, String status, String errorCode, long startedAfterMs, long durationMs) { }
}
