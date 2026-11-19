package io.github.opensabre.iac.integration;

import io.github.opensabre.security.context.InternalTokenUserContext;
import io.github.opensabre.security.token.InternalTokenClaims;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

/** Authorization uses verified claims, never browser roles or caller-provided headers. */
class ConsumerAccessTest {
    @Test void rejectsBrowserAndOtherInternalServicesAndAllowsTheConfiguredConsumer() {
        var context = new InternalTokenUserContext(new tools.jackson.databind.ObjectMapper());
        var access = new ConsumerAccess(context, Set.of("iqc-platform"));
        try {
            assertThatThrownBy(access::requireConsumer).hasMessageContaining("内部业务服务");
            context.bind(claims("another-service"));
            assertThatThrownBy(access::requireConsumer).hasMessageContaining("内部业务服务");
            context.bind(claims("iqc-platform"));
            assertThatCode(access::requireConsumer).doesNotThrowAnyException();
            context.clear();
            assertThatThrownBy(access::requireConsumer).hasMessageContaining("内部业务服务");
        } finally { context.clear(); }
    }
    private InternalTokenClaims claims(String source) {
        return new InternalTokenClaims(source,"alice","alice","iac-platform","test",0,0,60,source,
                "iac-platform",List.of(),List.of("ADMIN"),0,null,null,1,Map.of());
    }
}
