package com.portfolio.knowledge.domain;

public class DocumentException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final int status;

    public DocumentException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public DocumentException(String code, int status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
