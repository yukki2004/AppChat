package com.chatapp.core.auth.oauth.dto;


public record OAuthUserInfo(
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName
) {
}
