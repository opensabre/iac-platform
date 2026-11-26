package io.github.opensabre.iac.result.llm;

import com.openai.errors.OpenAIServiceException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/** Stable diagnostics derived from typed model errors without exposing remote payloads. */
public enum ModelFailure {
    CREDENTIAL_UNAVAILABLE("无法解析模型凭据，请检查密钥引用配置"),
    AUTHENTICATION_FAILED("供应商拒绝认证或授权，请检查凭据和模型访问权限"),
    MODEL_UNAVAILABLE("供应商未找到请求的资源，请检查模型名称和 Endpoint"),
    RATE_LIMITED("供应商限制调用，请检查速率限制和用量配额"),
    TIMEOUT("供应商调用超时，请检查网络和超时配置"),
    CONNECTION_FAILED("连接失败，请检查 Endpoint、密钥引用和模型名称");

    private final String message;

    ModelFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public static ModelFailure from(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SecretReferenceException || cause instanceof SecretReferenceException.InvalidFormat)
                return ModelFailure.CREDENTIAL_UNAVAILABLE;
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) return ModelFailure.TIMEOUT;
            if (cause instanceof OpenAIServiceException remote) {
                return switch (remote.statusCode()) {
                    case 401, 403 -> ModelFailure.AUTHENTICATION_FAILED;
                    case 404 -> ModelFailure.MODEL_UNAVAILABLE;
                    case 429 -> ModelFailure.RATE_LIMITED;
                    case 408, 504 -> ModelFailure.TIMEOUT;
                    default -> ModelFailure.CONNECTION_FAILED;
                };
            }
        }
        return ModelFailure.CONNECTION_FAILED;
    }

}
