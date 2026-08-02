package com.chatapp.core.auth.result;

import java.util.List;

/** `availableMethods` is kept as String (not the `twofactor` package's enum) since this DTO
 *  faces the Controller — auth/ doesn't need to know that domain's internal enum type.
 *  `preAuthToken` is NOT part of the response body — the Controller sets it as an HttpOnly
 *  cookie (Path=/auth/login/2fa), consistent with how access/refresh_token are transmitted. */
public record TwoFactorChallengeResult(String preAuthToken, long preAuthTokenTtlSeconds, List<String> availableMethods) implements LoginOutcome {
}
