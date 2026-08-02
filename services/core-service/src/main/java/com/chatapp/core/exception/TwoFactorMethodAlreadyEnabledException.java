package com.chatapp.core.exception;

public class TwoFactorMethodAlreadyEnabledException extends RuntimeException {

    public TwoFactorMethodAlreadyEnabledException(String message) {
        super(message);
    }
}
