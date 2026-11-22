package io.github.opensabre.iac.rest;

import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.github.opensabre.iac.modelprofile.IacModelProfileService;
import io.github.opensabre.iac.modelprofile.IacModelProfileService.ModelCommand;
import io.github.opensabre.iac.modelprofile.ModelConnectionTestService;
import io.github.opensabre.iac.modelprofile.ModelConnectionTestService.ConnectionTestResult;
import io.github.opensabre.iac.modelprofile.model.IacModelProfile;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Management API for reusable model profiles. */
@RestController
@RequestMapping("/api/iac/agent-assets/models")
public class IacModelProfileController {
    private final IacModelProfileService service;
    private final ModelConnectionTestService connectionTestService;
    public IacModelProfileController(IacModelProfileService service, ModelConnectionTestService connectionTestService) { this.service = service; this.connectionTestService = connectionTestService; }
    @GetMapping @ResourcePermission(code="iac:model:list", name="查看模型配置", type="iac", description="查询 Agent 模型配置")
    public List<ModelView> list() { return service.list().stream().map(ModelView::from).toList(); }
    @PostMapping @ResourcePermission(code="iac:model:create", name="管理模型配置", type="iac", description="创建 Agent 模型配置") @Audit(operationType=OperationType.CREATE, description="创建 IAC 模型配置", module="IAC_MODEL")
    public ModelView create(@RequestBody ModelCommand command) { return ModelView.from(service.create(command)); }
    @PutMapping("/{id}") @ResourcePermission(code="iac:model:update", name="管理模型配置", type="iac", description="修改 Agent 模型配置") @Audit(operationType=OperationType.UPDATE, description="修改 IAC 模型配置", module="IAC_MODEL")
    public ModelView update(@PathVariable String id, @RequestBody ModelCommand command) { return ModelView.from(service.update(id, command)); }
    @PostMapping("/{id}/enable") @ResourcePermission(code="iac:model:enable", name="管理模型配置", type="iac", description="启用 Agent 模型配置") @Audit(operationType=OperationType.UPDATE, description="启用 IAC 模型配置", module="IAC_MODEL")
    public ModelView enable(@PathVariable String id) { return ModelView.from(service.setEnabled(id, true)); }
    @PostMapping("/{id}/disable") @ResourcePermission(code="iac:model:disable", name="管理模型配置", type="iac", description="停用 Agent 模型配置") @Audit(operationType=OperationType.UPDATE, description="停用 IAC 模型配置", module="IAC_MODEL")
    public ModelView disable(@PathVariable String id) { return ModelView.from(service.setEnabled(id, false)); }
    @PostMapping("/{id}/test") @ResourcePermission(code="iac:model:test", name="测试模型连接", type="iac", description="测试 Agent 模型配置连通性")
    public ConnectionTestResult test(@PathVariable String id) { return connectionTestService.test(id); }

    public record ModelView(String id, String name, String code, String description, String provider,
                            String modelName, String endpoint, Double temperature, Integer timeoutSeconds,
                            Integer maxRetries, String status, Integer versionNo, boolean secretConfigured) {
        static ModelView from(IacModelProfile value) { return new ModelView(value.getId(), value.getName(), value.getCode(), value.getDescription(), value.getProvider(), value.getModelName(), value.getEndpoint(), value.getTemperature(), value.getTimeoutSeconds(), value.getMaxRetries(), value.getStatus(), value.getVersionNo(), value.getSecretRef() != null && !value.getSecretRef().isBlank()); }
    }
}
