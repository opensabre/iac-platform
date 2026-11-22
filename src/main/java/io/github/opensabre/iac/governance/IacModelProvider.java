package io.github.opensabre.iac.governance;

import io.github.opensabre.governance.dictionary.DictionaryEnum;
import io.github.opensabre.governance.dictionary.OpenSabreDictionary;

/** Model providers available to IAC Agent configurations. */
@OpenSabreDictionary(code = "iac_model_provider", name = "IAC 模型供应商")
public enum IacModelProvider implements DictionaryEnum {
    SPRING_AI("SPRING_AI", "Spring AI / OpenAI 兼容"),
    OPENAI("OPENAI", "OpenAI"),
    DASHSCOPE("DASHSCOPE", "阿里云百炼"),
    OLLAMA("OLLAMA", "Ollama 本地模型");

    private final String value;
    private final String label;
    IacModelProvider(String value, String label) { this.value = value; this.label = label; }
    @Override public String value() { return value; }
    @Override public String label() { return label; }
}
