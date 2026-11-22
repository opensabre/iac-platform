package io.github.opensabre.iac.governance;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** MCP transports supported by IAC Agent configurations. */
@OpenSabreDictionary(code = "iac_mcp_transport", name = "IAC MCP 传输类型")
public enum IacMcpTransport implements DictionaryEnum {
    STREAMABLE_HTTP("STREAMABLE_HTTP", "Streamable HTTP"),
    SSE("SSE", "SSE");

    private final String value;
    private final String label;
    IacMcpTransport(String value, String label) { this.value = value; this.label = label; }
    @Override public String value() { return value; }
    @Override public String label() { return label; }
}
