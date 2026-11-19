package io.github.opensabre.iac.governance;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** Lifecycle states shared by reusable Agent assets. */
@OpenSabreDictionary(code = "iac_agent_asset_status", name = "IAC Agent 资产状态")
public enum IacAssetStatus implements DictionaryEnum {
    ENABLED("ENABLED", "启用", "success"), DISABLED("DISABLED", "停用", "default");
    private final String value; private final String label; private final String tagType;
    IacAssetStatus(String value, String label, String tagType) { this.value = value; this.label = label; this.tagType = tagType; }
    @Override public String value() { return value; }
    @Override public String label() { return label; }
    @Override public String tagType() { return tagType; }
}
