package io.github.opensabre.iac.result.llm;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.jasypt.encryption.StringEncryptor;

/** Resolves the minimal `env:NAME` reference format from the deployment environment. */
@Component
public class EnvironmentSecretReferenceResolver implements SecretReferenceResolver {
    private final Environment environment;
    private final StringEncryptor encryptor;
    public EnvironmentSecretReferenceResolver(Environment environment) { this(environment, null); }
    @Autowired
    public EnvironmentSecretReferenceResolver(Environment environment, StringEncryptor encryptor) { this.environment = environment; this.encryptor = encryptor; }
    @Override public String resolve(String reference) {
        if (reference != null && reference.startsWith("ENC(")) {
            if (encryptor == null || !reference.endsWith(")") || reference.length() <= 5)
                throw new SecretReferenceException();
            try {
                String value = encryptor.decrypt(reference.substring(4, reference.length() - 1));
                if (value != null && !value.isBlank()) return value;
            } catch (RuntimeException ignored) {
                // Decryption errors can include details about the configured secret.
            }
            throw new SecretReferenceException();
        }
        if (reference == null || !reference.matches("env:[A-Z][A-Z0-9_]{1,127}"))
            throw new SecretReferenceException.InvalidFormat();
        String value = environment.getProperty(reference.substring(4));
        if (value == null || value.isBlank()) throw new SecretReferenceException();
        return value;
    }
}
