package io.github.opensabre.iac.governance;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IacDictionaryTest {
    @Test
    void agentModelAndMcpOptionsAreFrameworkDictionaries() {
        assertEquals("iac_model_provider", IacModelProvider.class.getAnnotation(OpenSabreDictionary.class).code());
        assertEquals("iac_mcp_transport", IacMcpTransport.class.getAnnotation(OpenSabreDictionary.class).code());
        assertEquals("iac_mcp_auth_type", IacMcpAuthType.class.getAnnotation(OpenSabreDictionary.class).code());
        assertEquals("iac_agent_asset_status", IacAssetStatus.class.getAnnotation(OpenSabreDictionary.class).code());
    }
}
