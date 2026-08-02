package com.chatapp.core.twofactor.dto.response;

/** `secret` is shown once for manual entry fallback; `otpauthUri` is what the client renders
 *  as a QR code (standard `otpauth://totp/...` format every authenticator app understands). */
public record TotpSetupResponse(String secret, String otpauthUri) {
}
