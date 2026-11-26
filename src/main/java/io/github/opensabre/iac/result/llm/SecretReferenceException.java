package io.github.opensabre.iac.result.llm;

/** Signals that a configured credential reference cannot be resolved. */
public class SecretReferenceException extends IllegalStateException {
    public SecretReferenceException() {
        super("无法解析模型密钥引用");
    }

    /** Indicates a reference format that the resolver cannot interpret. */
    public static final class InvalidFormat extends IllegalArgumentException {
        public InvalidFormat() {
            super("密钥配置格式无效，仅支持 env:NAME 或 ENC(...) 引用");
        }
    }
}
