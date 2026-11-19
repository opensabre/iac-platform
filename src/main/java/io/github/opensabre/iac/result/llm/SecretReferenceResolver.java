package io.github.opensabre.iac.result.llm;

/** Resolves a deployment-managed secret reference without exposing secret values to domain storage. */
public interface SecretReferenceResolver {
    String resolve(String reference);
}
