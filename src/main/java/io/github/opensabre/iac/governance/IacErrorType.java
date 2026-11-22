package io.github.opensabre.iac.governance;

import io.github.opensabre.common.core.exception.ErrorType;

public enum IacErrorType implements ErrorType {

    INVALID_ARGUMENT("IAC-1003", "请求参数不合法"),
    RESOURCE_NOT_FOUND("IAC-1004", "IAC 业务资源不存在"),
    ACCESS_DENIED("IAC-1005", "无权访问 IAC 业务资源"),
    INVALID_STATE("IAC-1006", "IAC 业务状态不允许当前操作");

    private final String code;
    private final String message;

    IacErrorType(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMesg() {
        return message;
    }
}
