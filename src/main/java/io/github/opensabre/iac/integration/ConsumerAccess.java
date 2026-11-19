package io.github.opensabre.iac.integration;

import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.security.context.InternalTokenUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Reuses verified internal-token claims; browser tokens cannot call the consumer API. */
@Component
public class ConsumerAccess {
    private final InternalTokenUserContext context;
    private final Set<String> consumers;

    public ConsumerAccess(InternalTokenUserContext context,
                          @Value("${iac.consumer.allowed-services:iqc-platform}") Set<String> consumers) {
        this.context = context;
        this.consumers = Set.copyOf(consumers);
    }

    public void requireConsumer() {
        var claims = context.currentClaims();
        if (claims == null || !consumers.contains(claims.source()))
            throw IacException.accessDenied("仅允许已授权的内部业务服务调用");
    }
}
