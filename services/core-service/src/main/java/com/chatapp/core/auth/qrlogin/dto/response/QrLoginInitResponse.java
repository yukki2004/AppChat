package com.chatapp.core.auth.qrlogin.dto.response;

public record QrLoginInitResponse(String qrToken, long expiresInSeconds) {
}
