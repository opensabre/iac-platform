package io.github.opensabre.iac.rest;

import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.github.opensabre.iac.mcp.IacMcpServerService;
import io.github.opensabre.iac.mcp.IacMcpServerService.McpCommand;
import io.github.opensabre.iac.mcp.model.IacMcpServer;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Management API for reusable MCP server assets. */
@RestController
@RequestMapping("/api/iac/agent-assets/mcps")
public class IacMcpServerController {
    private final IacMcpServerService service;
    private final io.github.opensabre.iac.mcp.dao.IacMcpServerMapper mapper;
    private final io.github.opensabre.iac.result.llm.SnapshotMcpToolProvider tools;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public IacMcpServerController(IacMcpServerService service, io.github.opensabre.iac.mcp.dao.IacMcpServerMapper mapper,
            io.github.opensabre.iac.result.llm.SnapshotMcpToolProvider tools, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.service = service; this.mapper=mapper; this.tools=tools; this.json=json;
    }
    @PostMapping("/{id}/discover-tools")
    @ResourcePermission(code="iac:mcp:discover", name="管理 MCP", type="iac", description="测试 MCP 连接并发现工具")
    public List<io.github.opensabre.iac.result.llm.SnapshotMcpToolProvider.DiscoveredTool> discover(@PathVariable String id) {
        var server=mapper.selectById(id);
        if (server == null) throw io.github.opensabre.iac.governance.IacException.notFound("MCP 不存在");
        try { return tools.discover(json.valueToTree(server)); }
        catch (RuntimeException error) { throw io.github.opensabre.iac.governance.IacException.invalidState("MCP 连接或工具发现失败，请检查地址、认证引用与超时配置"); }
    }
    @GetMapping @ResourcePermission(code="iac:mcp:list", name="查看 MCP", type="iac", description="查询 Agent MCP 资产")
    public List<IacMcpServer> list() { return service.list(); }
    @PostMapping @ResourcePermission(code="iac:mcp:create", name="管理 MCP", type="iac", description="创建 Agent MCP 资产") @Audit(operationType=OperationType.CREATE, description="创建 IAC MCP", module="IAC_MCP")
    public IacMcpServer create(@RequestBody McpCommand command) { return service.create(command); }
    @PutMapping("/{id}") @ResourcePermission(code="iac:mcp:update", name="管理 MCP", type="iac", description="修改 Agent MCP 资产") @Audit(operationType=OperationType.UPDATE, description="修改 IAC MCP", module="IAC_MCP")
    public IacMcpServer update(@PathVariable String id, @RequestBody McpCommand command) { return service.update(id, command); }
    @PostMapping("/{id}/enable") @ResourcePermission(code="iac:mcp:enable", name="管理 MCP", type="iac", description="启用 Agent MCP 资产") @Audit(operationType=OperationType.UPDATE, description="启用 IAC MCP", module="IAC_MCP")
    public IacMcpServer enable(@PathVariable String id) { return service.setEnabled(id, true); }
    @PostMapping("/{id}/disable") @ResourcePermission(code="iac:mcp:disable", name="管理 MCP", type="iac", description="停用 Agent MCP 资产") @Audit(operationType=OperationType.UPDATE, description="停用 IAC MCP", module="IAC_MCP")
    public IacMcpServer disable(@PathVariable String id) { return service.setEnabled(id, false); }
}
