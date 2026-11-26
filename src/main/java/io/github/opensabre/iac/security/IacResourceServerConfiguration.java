package io.github.opensabre.iac.security;

import io.github.opensabre.iac.consumer.ConsumerApplicationService;
import io.github.opensabre.security.actuator.ActuatorMonitoringAccess;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import io.github.opensabre.security.webmvc.InternalTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.stream.Stream;

/**
 * Verifies the external access token relayed by the gateway.
 *
 * <p>The gateway keeps the browser session, while IAC still needs the verified JWT
 * in its own Spring Security context so data-scope checks can see roles such as ADMIN.</p>
 */
@Configuration(proxyBeanMethods = false)
public class IacResourceServerConfiguration {

    @Bean
    SecurityFilterChain iacResourceServerFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            InternalTokenAuthenticationFilter internalTokenAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/actuator/internalTokenKeyStatus")
                        .hasAuthority(ActuatorMonitoringAccess.AUTHORITY)
                        .requestMatchers(ActuatorMonitoringAccess.metricPathArray())
                        .hasAnyAuthority("SCOPE_actuator.read", ActuatorMonitoringAccess.AUTHORITY)
                        .requestMatchers("/actuator/**")
                        .hasAuthority("SCOPE_actuator.read")
                        .requestMatchers(HttpMethod.POST, "/api/iac/v1/agents/*/invoke")
                        .hasAuthority("SCOPE_" + ConsumerApplicationService.INVOKE_SCOPE)
                        .requestMatchers("/api/iac/config/**", "/api/iac/agent-assets/**")
                        .access((authentication, context) -> {
                            var identity = authentication.get();
                            boolean application = identity.getAuthorities().stream().anyMatch(authority ->
                                    ("SCOPE_" + ConsumerApplicationService.INVOKE_SCOPE).equals(authority.getAuthority()));
                            return new AuthorizationDecision(identity.isAuthenticated() && !application
                                    && !(identity instanceof org.springframework.security.authentication.AnonymousAuthenticationToken));
                        })
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterBefore(internalTokenAuthenticationFilter,
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter iacJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roleAuthorities = new JwtGrantedAuthoritiesConverter();
        roleAuthorities.setAuthoritiesClaimName("roles");
        roleAuthorities.setAuthorityPrefix("");
        JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Stream.concat(
                        roleAuthorities.convert(jwt).stream(), scopeAuthorities.convert(jwt).stream())
                .toList());
        return converter;
    }
}
