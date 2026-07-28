package com.chatapp.core.auth;

import com.chatapp.core.auth.dto.response.UserResponse;

/** Kết quả nội bộ của login/register — KHÔNG serialize thẳng ra JSON, Controller tự tách
 *  accessToken/refreshToken ra Set-Cookie, chỉ trả UserResponse ở response body. */
public record AuthResult(
        UserResponse user,
        String accessToken,
        String refreshToken,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {
}
