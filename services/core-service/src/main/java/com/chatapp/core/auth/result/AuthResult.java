package com.chatapp.core.auth.result;

import com.chatapp.core.auth.dto.response.UserResponse;

/** Internal result of login — NOT serialized directly to JSON; the Controller extracts
 *  accessToken/refreshToken into Set-Cookie headers and only returns UserResponse in the body. */
public record AuthResult(
        UserResponse user,
        String accessToken,
        String refreshToken,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) implements LoginOutcome {
}
