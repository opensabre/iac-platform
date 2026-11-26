package io.github.opensabre.iac.rest;

import io.github.opensabre.boot.annotations.ResourcePermission;
import io.github.opensabre.governance.audit.annotations.Audit;
import io.github.opensabre.governance.audit.annotations.OperationType;
import io.github.opensabre.iac.consumer.ConsumerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Management endpoints for IAC grants and quotas assigned to registered OAuth clients. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/iac/config/consumer-applications")
@Tag(name = "智能体接入应用")
public class ConsumerApplicationController {
    private final ConsumerApplicationService applications;

    @GetMapping
    @Operation(summary = "查询接入应用")
    @ResourcePermission(code = "iac:consumer:list", type = "iac", name = "查看接入应用", description = "查询接入应用及用量")
    public List<ConsumerApplicationService.View> list() {
        return applications.list();
    }

    @PostMapping
    @Operation(summary = "登记接入应用")
    @ResourcePermission(code = "iac:consumer:create", type = "iac", name = "登记接入应用", description = "登记 OAuth 应用的 IAC 调用授权")
    @Audit(operationType = OperationType.CREATE, description = "登记接入应用", module = "IAC_CONSUMER")
    public ConsumerApplicationService.View create(@RequestBody ConsumerApplicationService.Command command) {
        return applications.create(command);
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "更新接入应用授权、配额或启用状态")
    @ResourcePermission(code = "iac:consumer:update", type = "iac", name = "管理接入应用", description = "管理接入应用的授权与配额")
    @Audit(operationType = OperationType.UPDATE, description = "更新接入应用", module = "IAC_CONSUMER", key = "#clientId")
    public ConsumerApplicationService.View update(@PathVariable String clientId, @RequestBody ConsumerApplicationService.Command command) {
        return applications.update(clientId, command);
    }
}
