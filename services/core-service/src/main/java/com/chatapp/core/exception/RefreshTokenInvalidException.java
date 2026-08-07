package com.chatapp.core.exception;

public class RefreshTokenInvalidException extends RuntimeException {

    public RefreshTokenInvalidException(String message) {
        super(message);
    }
}
