package io.github.opensabre.iac.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.agent.AgentService;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.shared.IacDataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** Manages IAC consumer grants and checks them against the verified machine token on every call. */
@Service
@RequiredArgsConstructor
public class ConsumerApplicationService {
    public static final String INVOKE_SCOPE = "iac.invoke";

    private final ConsumerApplicationMapper mapper;
    private final AgentService agents;
    private final IacDataScope scope;
    private final ObjectMapper json;

    public List<View> list() {
        requireAdmin();
        return mapper.selectList(Wrappers.<ConsumerApplication>lambdaQuery()
                .orderByAsc(ConsumerApplication::getClientId)).stream().map(this::view).toList();
    }

    public View create(Command command) {
        requireAdmin();
        var row = policy(command);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw IacException.invalidArgument("接入应用已存在");
        }
        return view(row);
    }

    public View update(String clientId, Command command) {
        requireAdmin();
        var row = policy(command);
        if (!row.getClientId().equals(clientId)) throw IacException.invalidArgument("客户端 ID 不可修改");
        if (mapper.updateById(row) != 1) throw IacException.notFound("接入应用不存在");
        return view(mapper.selectById(clientId));
    }

    /** Authorizes an exact published version and consumes one request from both quota windows. */
    public String authorize(Jwt jwt, String agentId, int versionNo) {
        if (jwt == null || !validClientId(jwt.getSubject())
                || !List.of(jwt.getSubject()).equals(jwt.getAudience())
                || !scopes(jwt).contains(INVOKE_SCOPE))
            throw IacException.accessDenied("需要已登记应用的 OAuth 客户端令牌");
        String clientId = jwt.getSubject();
        var row = mapper.selectById(clientId);
        if (row == null || !Boolean.TRUE.equals(row.getEnabled())
                || !grants(row).contains(new Grant(agentId, versionNo)))
            throw IacException.accessDenied("该应用未获授权调用指定智能体版本");
        long now = Instant.now().getEpochSecond();
        if (mapper.reserve(clientId, row.getGrantsJson(), now / 60, now / 86_400) != 1)
            throw IacException.quotaExceeded("调用配额已用尽或应用授权已变更");
        return clientId;
    }

    private ConsumerApplication policy(Command value) {
        if (value == null || !validClientId(value.clientId()) || value.displayName() == null
                || value.displayName().isBlank() || value.displayName().length() > 120 || value.enabled() == null
                || value.perMinute() == null || value.perMinute() < 1 || value.perMinute() > 10_000
                || value.perDay() == null || value.perDay() < 1 || value.perDay() > 1_000_000
                || value.grants() == null || value.grants().size() > 100)
            throw IacException.invalidArgument("客户端 ID、名称、限额或版本授权无效");
        var unique = new HashSet<Grant>();
        for (Grant grant : value.grants()) {
            if (grant == null || grant.agentId() == null || !grant.agentId().matches("[A-Za-z0-9_-]{1,64}")
                    || grant.versionNo() < 1 || !unique.add(grant))
                throw IacException.invalidArgument("版本授权无效或重复");
            if (value.enabled() && !"PUBLISHED".equals(agents.version(grant.agentId(), grant.versionNo()).getStatus()))
                throw IacException.invalidArgument("应用授权需要已发布版本");
        }
        var row = new ConsumerApplication();
        row.setClientId(value.clientId());
        row.setDisplayName(value.displayName().strip());
        row.setEnabled(value.enabled());
        row.setPerMinute(value.perMinute());
        row.setPerDay(value.perDay());
        try {
            row.setGrantsJson(json.writeValueAsString(value.grants()));
        } catch (JsonProcessingException invalid) {
            throw IacException.invalidArgument("无法保存版本授权");
        }
        return row;
    }

    private static boolean validClientId(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,128}");
    }

    private static List<String> scopes(Jwt jwt) {
        Object scope = jwt.getClaims().get("scope");
        if (scope instanceof String text) return List.of(text.split("\\s+"));
        if (scope instanceof List<?> values) return values.stream().filter(String.class::isInstance)
                .map(String.class::cast).toList();
        return List.of();
    }

    private void requireAdmin() {
        if (!scope.isAdministrator()) throw IacException.accessDenied("仅平台管理员可以管理接入应用");
    }

    private List<Grant> grants(ConsumerApplication row) {
        try {
            return json.readValue(row.getGrantsJson(), new TypeReference<>() {});
        } catch (JsonProcessingException invalid) {
            throw IacException.invalidState("无法读取版本授权");
        }
    }

    private View view(ConsumerApplication row) {
        long now = Instant.now().getEpochSecond();
        return new View(row.getClientId(), row.getDisplayName(), row.getEnabled(), row.getPerMinute(), row.getPerDay(),
                Long.valueOf(now / 60).equals(row.getMinuteWindow()) ? row.getMinuteUsed() : 0,
                Long.valueOf(now / 86_400).equals(row.getDayWindow()) ? row.getDayUsed() : 0, grants(row));
    }

    public record Grant(String agentId, int versionNo) { }
    public record Command(String clientId, String displayName, Boolean enabled, Integer perMinute,
                          Integer perDay, List<Grant> grants) { }
    public record View(String clientId, String displayName, boolean enabled, int perMinute, int perDay,
                       int requestsThisMinute, int requestsToday, List<Grant> grants) { }
}
