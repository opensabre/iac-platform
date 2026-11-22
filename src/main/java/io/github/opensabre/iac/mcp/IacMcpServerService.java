package io.github.opensabre.iac.mcp;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.governance.IacMcpAuthType;
import io.github.opensabre.iac.governance.IacMcpTransport;
import io.github.opensabre.iac.mcp.dao.IacMcpServerMapper;
import io.github.opensabre.iac.mcp.model.IacMcpServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/** Manages reusable MCP endpoints without accepting credential material. */
@Service
public class IacMcpServerService {
    private final IacMcpServerMapper mapper; private final ObjectMapper objectMapper;
    public IacMcpServerService(IacMcpServerMapper mapper, ObjectMapper objectMapper) { this.mapper = mapper; this.objectMapper = objectMapper; }
    public List<IacMcpServer> list() { return mapper.selectList(Wrappers.<IacMcpServer>lambdaQuery().orderByAsc(IacMcpServer::getStatus).orderByDesc(IacMcpServer::getCreatedTime)); }

    /** Creates an enabled MCP endpoint with an UNKNOWN health state until explicitly tested. */
    @Transactional
    public IacMcpServer create(McpCommand command) {
        validate(command);
        if (findByCode(command.code()) != null) throw IacException.invalidState("MCP 编码已存在: " + command.code());
        IacMcpServer server = new IacMcpServer(); apply(server, command);
        server.setStatus("ENABLED"); server.setHealthStatus("UNKNOWN"); server.setVersionNo(1); mapper.insert(server); return server;
    }
    /** Updates connection metadata and resets health because the endpoint configuration changed. */
    @Transactional
    public IacMcpServer update(String id, McpCommand command) {
        IacMcpServer server = require(id); command = command.withCode(server.getCode()); validate(command); apply(server, command);
        server.setHealthStatus("UNKNOWN"); server.setVersionNo(server.getVersionNo() + 1); mapper.updateById(server); return server;
    }
    @Transactional
    public IacMcpServer setEnabled(String id, boolean enabled) { IacMcpServer server = require(id); server.setStatus(enabled ? "ENABLED" : "DISABLED"); mapper.updateById(server); return server; }

    private void validate(McpCommand value) {
        if (blank(value.name()) || blank(value.code()) || blank(value.transport()) || blank(value.endpoint()) || blank(value.authType())) throw IacException.invalidArgument("MCP 名称、编码、传输类型、地址和认证方式不能为空");
        if (!value.code().matches("[A-Z][A-Z0-9_]{1,63}")) throw IacException.invalidArgument("MCP 编码必须为大写字母、数字或下划线");
        if (Arrays.stream(IacMcpTransport.values()).noneMatch(item -> item.value().equals(value.transport()))) throw IacException.invalidArgument("不支持的 MCP 传输类型");
        if (Arrays.stream(IacMcpAuthType.values()).noneMatch(item -> item.value().equals(value.authType()))) throw IacException.invalidArgument("不支持的 MCP 认证方式");
        if (!"NONE".equals(value.authType()) && blank(value.secretRef())) throw IacException.invalidArgument("启用认证时必须配置密钥引用");
        if (value.secretRef() != null && value.secretRef().length() > 255) throw IacException.invalidArgument("密钥引用不能超过 255 字符");
        try { URI uri = URI.create(value.endpoint()); if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException(); }
        catch (Exception exception) { throw IacException.invalidArgument("MCP 地址必须是有效的 HTTP(S) URL"); }
        if (value.timeoutSeconds() == null || value.timeoutSeconds() < 1 || value.timeoutSeconds() > 300) throw IacException.invalidArgument("MCP 超时时间必须在 1 到 300 秒之间");
        if (!blank(value.allowedToolsJson())) try { if (!objectMapper.readTree(value.allowedToolsJson()).isArray()) throw IacException.invalidArgument("工具白名单必须是 JSON 数组"); }
        catch (IacException exception) { throw exception; } catch (Exception exception) { throw IacException.invalidArgument("工具白名单不是有效 JSON", exception); }
    }
    private IacMcpServer findByCode(String code) { return mapper.selectOne(Wrappers.<IacMcpServer>lambdaQuery().eq(IacMcpServer::getCode, code)); }
    private IacMcpServer require(String id) { IacMcpServer server = mapper.selectById(id); if (server == null) throw IacException.notFound("MCP 不存在: " + id); return server; }
    private static void apply(IacMcpServer target, McpCommand source) { target.setName(source.name().trim()); target.setCode(source.code().trim()); target.setDescription(source.description()); target.setTransport(source.transport()); target.setEndpoint(source.endpoint().trim()); target.setAuthType(source.authType()); target.setSecretRef("NONE".equals(source.authType()) ? null : source.secretRef()); target.setTimeoutSeconds(source.timeoutSeconds()); target.setAllowedToolsJson(source.allowedToolsJson()); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record McpCommand(String name, String code, String description, String transport, String endpoint,
                             String authType, String secretRef, Integer timeoutSeconds, String allowedToolsJson) {
        McpCommand withCode(String stableCode) { return new McpCommand(name, stableCode, description, transport, endpoint, authType, secretRef, timeoutSeconds, allowedToolsJson); }
    }
}
