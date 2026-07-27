package com.chatapp.core.auth;

import com.chatapp.core.auth.dto.response.PublicUserDTO;

/** Kết quả nội bộ của login/register — KHÔNG serialize thẳng ra JSON, Controller tự tách
 *  accessToken/refreshToken ra Set-Cookie, chỉ trả PublicUserDTO ở response body. */
public record AuthResult(
        PublicUserDTO user,
        String accessToken,
        String refreshToken,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {
}
