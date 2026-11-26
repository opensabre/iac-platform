package io.github.opensabre.iac.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.agent.dao.AgentChatSessionMapper;
import io.github.opensabre.iac.agent.dao.AgentVersionMapper;
import io.github.opensabre.iac.agent.model.AgentChatSession;
import io.github.opensabre.iac.agent.model.AgentVersion;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.invocation.InvocationHistory;
import io.github.opensabre.iac.shared.IacDataScope;
import io.github.opensabre.iac.shared.IacPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Stores successful saved-version test conversations without holding a transaction during model calls. */
@Service
@RequiredArgsConstructor
public class AgentChatSessionService {
    private final AgentChatSessionMapper sessions;
    private final AgentVersionMapper versions;
    private final AgentChatTestService chat;
    private final IacDataScope scope;
    private final ObjectMapper json;
    private final InvocationHistory invocations;

    /** Temporary configurations remain ephemeral; saved versions require server-verified history. */
    public ChatResult chat(AgentChatTestService.Request request) {
        if (request == null) throw IacException.invalidArgument("请提供聊天请求");
        if (request.agentId() == null) {
            if (request.sessionId() != null) throw IacException.invalidArgument("临时配置不能继续已保存会话");
            var execution = invocations.start("TEST", scope.owner(), null, null,
                    null, null, scope.owner(), scope.groupId());
            return result(chat(request, execution), null, execution.requestId());
        }
        if (request.versionNo() == null || request.messages() == null || request.messages().isEmpty())
            throw IacException.invalidArgument("请提供智能体版本和聊天消息");
        var version = versions.selectOne(Wrappers.<AgentVersion>lambdaQuery()
                .eq(AgentVersion::getAgentId, request.agentId())
                .eq(AgentVersion::getVersionNo, request.versionNo()));
        if (version == null) throw IacException.notFound("智能体版本不存在");
        String hash = configHash(version.getConfigJson());
        AgentChatSession session = request.sessionId() == null ? null : requireSession(request.agentId(), request.sessionId());
        List<SavedMessage> history = session == null ? new ArrayList<>() : new ArrayList<>(messages(session));
        if (session != null) {
            if (!"TEST".equals(session.getSource())) throw IacException.accessDenied("仅可从测试聊天继续测试会话");
            if (!Objects.equals(scope.owner(), session.getOwner())) throw IacException.accessDenied("只能继续本人发起的会话");
            if (!Objects.equals(session.getVersionNo(), request.versionNo()) || !hash.equals(session.getConfigHash()))
                throw IacException.invalidState("智能体配置已切换，请重新开始会话");
        }
        if (request.messages().size() != history.size() + 1)
            throw IacException.invalidState("会话上下文不一致，请重新开始会话");
        for (int index = 0; index < history.size(); index++) {
            var saved = history.get(index); var supplied = request.messages().get(index);
            if (supplied == null || !Objects.equals(saved.role(), supplied.role()) || !Objects.equals(saved.content(), supplied.content()))
                throw IacException.invalidState("会话上下文不一致，请重新开始会话");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        String sessionId = session == null ? UUID.randomUUID().toString() : session.getId();
        String owner = scope.owner();
        String group = session == null ? scope.groupId() : session.getOwnerGroupId();
        var execution = invocations.start("TEST", owner, request.agentId(), request.versionNo(),
                sessionId, null, owner, group);
        var reply = chat(request, execution);
        LocalDateTime now = LocalDateTime.now();
        var user = request.messages().getLast();
        addTurn(history, user.content(), reply, execution.requestId(), startedAt, now);
        if (session == null) {
            session = saveNewSession(sessionId, request.agentId(), request.versionNo(), "TEST", owner, group,
                    hash, startedAt, now, history);
        } else {
            // One atomic update prevents concurrent replies from overwriting committed history.
            int updated = sessions.update(null, Wrappers.<AgentChatSession>lambdaUpdate()
                    .eq(AgentChatSession::getId, session.getId()).eq(AgentChatSession::getMessageCount, history.size() - 2)
                    .set(AgentChatSession::getMessagesJson, writeMessages(history)).set(AgentChatSession::getMessageCount, history.size())
                    .set(AgentChatSession::getLastMessageTime, now).set(AgentChatSession::getUpdatedTime, java.util.Date.from(now.atZone(java.time.ZoneId.systemDefault()).toInstant())));
            if (updated != 1) throw IacException.invalidState("会话已更新，请重新开始会话");
        }
        return result(reply, session.getId(), execution.requestId());
    }

    /** Creates a one-turn session for a successful application call to an authorized published version. */
    public ChatResult invokePublished(AgentVersion version, String caller, String input) {
        String sessionId = UUID.randomUUID().toString();
        var startedAt = LocalDateTime.now();
        var execution = invocations.start("API", caller, version.getAgentId(), version.getVersionNo(),
                sessionId, null, null, null);
        try {
            var reply = chat.published(version.getConfigJson(), input, execution);
            var now = LocalDateTime.now();
            List<SavedMessage> history = new ArrayList<>();
            addTurn(history, input, reply, execution.requestId(), startedAt, now);
            saveNewSession(sessionId, version.getAgentId(), version.getVersionNo(), "API", caller, null,
                    configHash(version.getConfigJson()), startedAt, now, history);
            return result(reply, sessionId, execution.requestId());
        } catch (RuntimeException failure) {
            execution.failed(execution.failureCode("EXECUTION_FAILED"));
            throw new PublishedInvocationFailure(execution.requestId());
        }
    }

    /** Safe error used by the application-facing API after a request ID has been assigned. */
    public static final class PublishedInvocationFailure extends RuntimeException {
        private final String requestId;

        private PublishedInvocationFailure(String requestId) {
            super("智能体调用未完成");
            this.requestId = requestId;
        }

        public String requestId() {
            return requestId;
        }
    }

    private void addTurn(List<SavedMessage> history, String input, AgentChatTestService.Result reply,
                         String requestId, LocalDateTime startedAt, LocalDateTime finishedAt) {
        history.add(new SavedMessage("user", input, startedAt.toString(), null, null, List.of()));
        history.add(new SavedMessage("assistant", reply.content(), finishedAt.toString(), reply.model(),
                reply.latencyMillis(), reply.calledTools(), requestId));
    }

    private AgentChatSession saveNewSession(String id, String agentId, Integer version, String source, String owner,
            String group, String hash, LocalDateTime startedAt, LocalDateTime now, List<SavedMessage> history) {
        var session = new AgentChatSession();
        session.setId(id);
        session.setAgentId(agentId);
        session.setVersionNo(version);
        session.setSource(source);
        String title = SessionTextRedaction.mask(history.getFirst().content()).strip().replaceAll("\\s+", " ");
        // Truncate by code point to preserve supplementary characters.
        session.setTitle(title.substring(0, title.offsetByCodePoints(0, Math.min(80, title.codePointCount(0, title.length())))));
        session.setOwner(owner);
        session.setCaller(owner);
        session.setOwnerGroupId(group);
        session.setConfigHash(hash);
        session.setMessageCount(history.size());
        session.setMessagesJson(writeMessages(history));
        session.setLastMessageTime(now);
        session.setCreatedTime(java.util.Date.from(startedAt.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        session.setUpdatedTime(session.getCreatedTime());
        sessions.insert(session);
        return session;
    }

    private AgentChatTestService.Result chat(AgentChatTestService.Request request, InvocationHistory.Execution execution) {
        try {
            return chat.chat(request, execution);
        } catch (RuntimeException failure) {
            execution.failed(execution.failureCode("EXECUTION_FAILED"));
            throw failure;
        }
    }

    /** Queries only summary columns; message bodies are loaded by the authorized detail endpoint. */
    public IacPage<Summary> page(String agentId, long current, long size, String title, String owner,
                                 LocalDateTime startedFrom, LocalDateTime startedTo) {
        return page(agentId, current, size, title, owner, startedFrom, startedTo, new Filters(null, null, null, null, null));
    }

    /** Applies the same data scope to advanced filters used by the standalone and agent-specific views. */
    public IacPage<Summary> page(String agentId, long current, long size, String title, String owner,
                                 LocalDateTime startedFrom, LocalDateTime startedTo, Filters filters) {
        if (startedFrom != null && startedTo != null && startedFrom.isAfter(startedTo))
            throw IacException.invalidArgument("开始时间不能晚于结束时间");
        if (filters.executionStatus() != null && !List.of("SUCCEEDED", "FAILED", "RUNNING").contains(filters.executionStatus()))
            throw IacException.invalidArgument("执行状态无效");
        var query = Wrappers.<AgentChatSession>lambdaQuery()
                .select(AgentChatSession::getId, AgentChatSession::getAgentId, AgentChatSession::getVersionNo,
                        AgentChatSession::getTitle, AgentChatSession::getSource, AgentChatSession::getOwner, AgentChatSession::getCaller,
                        AgentChatSession::getMessageCount, AgentChatSession::getCreatedTime, AgentChatSession::getLastMessageTime)
                .eq(agentId != null && !agentId.isBlank(), AgentChatSession::getAgentId, agentId)
                .eq(filters.sessionId() != null && !filters.sessionId().isBlank(), AgentChatSession::getId, filters.sessionId())
                .eq(filters.versionNo() != null, AgentChatSession::getVersionNo, filters.versionNo())
                .eq(filters.source() != null && !filters.source().isBlank(), AgentChatSession::getSource, filters.source())
                .eq(filters.caller() != null && !filters.caller().isBlank(), AgentChatSession::getCaller, filters.caller())
                .apply(filters.executionStatus() != null,
                        "EXISTS (SELECT 1 FROM iac_agent_invocation invocation WHERE invocation.session_id = iac_agent_chat_session.id AND invocation.status = {0})",
                        filters.executionStatus())
                .like(title != null && !title.isBlank(), AgentChatSession::getTitle, title)
                .like(owner != null && !owner.isBlank(), AgentChatSession::getOwner, owner)
                .ge(startedFrom != null, AgentChatSession::getCreatedTime, startedFrom)
                .le(startedTo != null, AgentChatSession::getCreatedTime, startedTo);
        if (!scope.canViewAll()) {
            String group = scope.groupId();
            query.ne(AgentChatSession::getSource, "API").and(q -> q.eq(AgentChatSession::getOwner, scope.owner())
                    .or(group != null, nested -> nested.eq(AgentChatSession::getOwnerGroupId, group)));
        }
        var page = sessions.selectPage(new Page<>(Math.max(1, current), Math.min(100, Math.max(1, size))),
                query.orderByDesc(AgentChatSession::getLastMessageTime).orderByDesc(AgentChatSession::getId));
        return new IacPage<>(page.getRecords().stream().map(this::summary).toList(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    /** Checks both the route's agent and the current user's data scope before returning any messages. */
    public Detail detail(String agentId, String sessionId) {
        var session = requireSession(agentId, sessionId);
        var redacted = messages(session).stream().map(message -> new SavedMessage(message.role(),
                SessionTextRedaction.mask(message.content()), message.time(), message.model(), message.latencyMillis(),
                message.calledTools(), message.requestId())).toList();
        return new Detail(summary(session), redacted, invocations.forSession(sessionId,
                redacted.stream().map(SavedMessage::requestId).filter(Objects::nonNull).toList()));
    }

    private AgentChatSession requireSession(String agentId, String sessionId) {
        var session = sessions.selectById(sessionId);
        if (session == null || !Objects.equals(agentId, session.getAgentId())) throw IacException.notFound("会话不存在");
        if (("API".equals(session.getSource()) && !scope.canViewAll())
                || !scope.canView(session.getOwner(), session.getOwnerGroupId())) throw IacException.accessDenied("无权查看该会话");
        return session;
    }

    private List<SavedMessage> messages(AgentChatSession session) {
        try { return json.readValue(session.getMessagesJson(), new TypeReference<>() {}); }
        catch (Exception error) { throw IacException.invalidState("会话记录读取失败"); }
    }

    private String writeMessages(List<SavedMessage> messages) {
        try { return json.writeValueAsString(messages); }
        catch (Exception error) { throw IacException.invalidState("会话记录保存失败"); }
    }

    private String configHash(String config) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(config.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw IacException.invalidState("智能体配置读取失败"); }
    }

    private Summary summary(AgentChatSession s) {
        return new Summary(s.getId(), s.getAgentId(), s.getVersionNo(), SessionTextRedaction.mask(s.getTitle()), s.getSource(), s.getOwner(),
                s.getCaller(), s.getMessageCount(), s.getCreatedTime() == null ? null : s.getCreatedTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(), s.getLastMessageTime());
    }

    private ChatResult result(AgentChatTestService.Result reply, String sessionId, String requestId) {
        return new ChatResult(reply.content(), reply.latencyMillis(), reply.model(), reply.calledTools(), sessionId, requestId);
    }

    public record Filters(String sessionId, Integer versionNo, String source, String caller, String executionStatus) { }

    /** Public views intentionally omit credential-bearing configuration and internal persistence fields. */
    public record Summary(String id, String agentId, Integer versionNo, String title, String source, String owner,
                          String caller, Integer messageCount, LocalDateTime createdTime, LocalDateTime lastMessageTime) { }
    public record SavedMessage(String role, String content, String time, String model, Long latencyMillis,
                               List<String> calledTools, String requestId) {
        public SavedMessage(String role, String content, String time, String model, Long latencyMillis, List<String> calledTools) {
            this(role, content, time, model, latencyMillis, calledTools, null);
        }
    }
    public record Detail(Summary session, List<SavedMessage> messages, List<InvocationHistory.Detail> executions) { }
    public record ChatResult(String content, long latencyMillis, String model, List<String> calledTools, String sessionId, String requestId) { }
}
