package io.github.opensabre.iac.governance;

import io.github.opensabre.common.core.exception.BaseException;

/** Domain exception mapped by OpenSabre's standard WebMVC error handler. */
public final class IacException extends BaseException {
    private IacException(IacErrorType type, String message) {
        super(type, message);
    }

    private IacException(IacErrorType type, String message, Throwable cause) {
        super(type, message, cause);
    }

    public static IacException invalidArgument(String message) {
        return new IacException(IacErrorType.INVALID_ARGUMENT, message);
    }

    public static IacException invalidArgument(String message, Throwable cause) {
        return new IacException(IacErrorType.INVALID_ARGUMENT, message, cause);
    }

    public static IacException notFound(String message) {
        return new IacException(IacErrorType.RESOURCE_NOT_FOUND, message);
    }

    public static IacException accessDenied(String message) {
        return new IacException(IacErrorType.ACCESS_DENIED, message);
    }

    public static IacException invalidState(String message) {
        return new IacException(IacErrorType.INVALID_STATE, message);
    }

}
