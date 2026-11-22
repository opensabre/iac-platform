package io.github.opensabre.iac.governance;

import io.github.opensabre.governance.errorcatalog.ErrorCatalogProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IacGovernanceConfiguration {

    @Bean
    ErrorCatalogProvider iacErrorCatalogProvider() {
        return ErrorCatalogProvider.of("iac", IacErrorType.values());
    }
}
