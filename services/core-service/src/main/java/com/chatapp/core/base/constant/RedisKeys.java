package com.chatapp.core.base.constant;

import java.util.UUID;

/** Every Redis key pattern + its TTL, together — one place, so key format and TTL can never
 *  drift out of sync with each other or get hand-typed twice at the call site. */
public final class RedisKeys {

    public static final long PRE_AUTH_TOKEN_TTL_SECONDS = 300;
    public static final long PENDING_TOTP_SECRET_TTL_SECONDS = 600;

    public static String preAuth(String preAuthToken) {
        return "cache:pre_auth:" + preAuthToken;
    }

    /** Holds the Base32 secret between /2fa/totp/setup and /2fa/totp/confirm — nothing is
     *  written to two_factor_methods until the user proves they configured their app
     *  correctly, so this must never be the persisted row itself. */
    public static String pendingTotpSecret(UUID userId) {
        return "cache:pending_totp_secret:" + userId;
    }

    /** Set when an OTP code exhausts its attempt limit — blocks OtpCodeService.generate() from
     *  issuing a fresh code for the same (target, purpose) until it expires. TTL =
     *  OtpProperties.lockoutSeconds, set at the call site (the value isn't fixed like the
     *  other keys here since it's configurable per deployment, not a code-level constant). */
    public static String otpLockout(String target, OtpPurpose purpose) {
        return "cache:otp_lockout:" + purpose.name() + ":" + target;
    }

    private RedisKeys() {
    }
}
