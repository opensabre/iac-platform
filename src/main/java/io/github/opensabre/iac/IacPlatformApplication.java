package io.github.opensabre.iac;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.governance.audit.annotations.EnabledAudit;
import io.github.opensabre.iac.shared.IacOrganizationClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/** Independent owner of agent capabilities and conversation history. */
@EnabledAudit
@SpringBootApplication
@EnableMethodCache(basePackages = "io.github.opensabre.iac")
@EnableFeignClients(clients = IacOrganizationClient.class)
public class IacPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(IacPlatformApplication.class, args);
    }

    @Bean
    ObjectMapper iacObjectMapper() { return new ObjectMapper(); }
}
