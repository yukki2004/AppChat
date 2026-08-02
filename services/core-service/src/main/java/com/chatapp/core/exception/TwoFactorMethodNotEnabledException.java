package com.chatapp.core.exception;

public class TwoFactorMethodNotEnabledException extends RuntimeException {

    public TwoFactorMethodNotEnabledException(String message) {
        super(message);
    }
}
