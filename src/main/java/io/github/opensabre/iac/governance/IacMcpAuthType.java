package io.github.opensabre.iac.governance;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** Authentication modes supported by managed MCP servers. */
@OpenSabreDictionary(code = "iac_mcp_auth_type", name = "IAC MCP 认证方式")
public enum IacMcpAuthType implements DictionaryEnum {
    NONE("NONE", "无认证"), BEARER("BEARER", "Bearer Token"), HEADER("HEADER", "自定义请求头");
    private final String value; private final String label;
    IacMcpAuthType(String value, String label) { this.value = value; this.label = label; }
    @Override public String value() { return value; }
    @Override public String label() { return label; }
}
