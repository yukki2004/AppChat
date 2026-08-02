package com.chatapp.core.auth.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Returned when the password is correct but the account has 2FA enabled — does NOT set the
 *  access/refresh_token cookies, and does NOT send any code yet. Field name overridden to
 *  match the exact API contract (`requires_2fa`), since the automatic snake_case conversion
 *  can't derive it correctly from `requiresTwoFactor` (the digit breaks the normal word
 *  boundary split). No `pre_auth_token` field here — it's an HttpOnly cookie, not part of the
 *  body. The client must call `/auth/login/2fa/challenge` with a method picked from
 *  `availableMethods` before any code is sent. */
public record TwoFactorRequiredResponse(
        @JsonProperty("requires_2fa") boolean requiresTwoFactor,
        List<String> availableMethods) {
}
