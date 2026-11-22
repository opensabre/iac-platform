package io.github.opensabre.iac.governance;

import io.github.opensabre.boot.entity.ResourceMappingSnapshot;
import io.github.opensabre.boot.metadata.OpensabreVersion;
import io.github.opensabre.boot.rest.MappingInfoHandler;
import io.github.opensabre.common.core.entity.vo.Result;
import io.github.opensabre.governance.registration.GovernanceRegistrationCoordinator;
import io.github.opensabre.iac.shared.IacOrganizationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Registers IAC's complete permission-resource snapshot in OpenSabre Organization after startup.
 */
@Component
@ConditionalOnProperty(prefix = "opensabre.resource-registration", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class IacResourceRegistrationListener {
    private final MappingInfoHandler mappingInfoHandler;
    private final IacOrganizationClient organizationClient;
    private final Environment environment;
    private final GovernanceRegistrationCoordinator registrationCoordinator;

    /** Schedules bounded, observable registration without blocking application startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        registrationCoordinator.submit("resource-permissions", this::registerOnce);
    }

    private void registerOnce() {
        String application = environment.getProperty("spring.application.name", "iac-platform");
        String token = environment.getProperty("opensabre.governance.registration-token", "");
        if (token.isBlank()) throw new IllegalStateException("IAC resource registration token is missing");
        ResourceMappingSnapshot snapshot = ResourceMappingSnapshot.builder()
                .application(application)
                .version(OpensabreVersion.getVersion())
                .resources(mappingInfoHandler.getMappingInfo())
                .build();
        Result<IacOrganizationClient.ResourceRegistrationResult> response = organizationClient.registerResources(
                application, true, token, snapshot);
        IacOrganizationClient.ResourceRegistrationResult result = response.getData();
        if (result == null) throw new IllegalStateException("IAC resource registration returned no data");
        log.info("IAC resources registered: application={} created={} updated={} stale={} total={}",
                application, result.created(), result.updated(), result.stale(), result.total());
    }
}
