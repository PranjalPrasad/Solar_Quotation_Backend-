package com.solar.Quotation.exception;

public class ForbiddenException extends RuntimeException {
    private final String errorCode;

    public ForbiddenException(String message) { this("FORBIDDEN", message); }
    public ForbiddenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}
