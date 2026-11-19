package io.github.opensabre.iac.integration;

import io.github.opensabre.iac.agent.AgentAssetReferenceValidator;
import io.github.opensabre.iac.agent.AgentConfiguration;
import io.github.opensabre.iac.agent.AgentService;
import io.github.opensabre.iac.agent.model.Agent;
import io.github.opensabre.iac.agent.model.AgentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Internal read/resolve contract for consumers; IAC remains the only writer. */
@RestController
@RequestMapping("/internal/iac")
@RequiredArgsConstructor
public class AgentConsumerController {
    private final ConsumerAccess access;
    private final AgentService agents;
    private final AgentAssetReferenceValidator assets;

    @GetMapping("/agents/{id}")
    public Agent agent(@PathVariable String id) {
        access.requireConsumer();
        return agents.get(id);
    }

    @GetMapping("/agents/{id}/versions/{versionNo}")
    public AgentVersion version(@PathVariable String id, @PathVariable int versionNo) {
        access.requireConsumer();
        return agents.version(id, versionNo);
    }

    @PostMapping("/assets/validate")
    public boolean validate(@RequestBody AgentConfiguration configuration) {
        access.requireConsumer();
        assets.validate(configuration);
        return true;
    }

    @PostMapping("/assets/snapshot")
    public AgentConfiguration.AssetSnapshots snapshot(@RequestBody AgentConfiguration configuration) {
        access.requireConsumer();
        return assets.snapshot(configuration);
    }
}
