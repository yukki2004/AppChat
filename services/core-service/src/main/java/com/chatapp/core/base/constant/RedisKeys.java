package com.chatapp.core.base.constant;

import java.util.UUID;

/** Every Redis key pattern + its TTL, together — one place, so key format and TTL can never
 *  drift out of sync with each other or get hand-typed twice at the call site. */
public final class RedisKeys {

    public static final long PRE_AUTH_TOKEN_TTL_SECONDS = 300;
    public static final long PENDING_TOTP_SECRET_TTL_SECONDS = 600;
    public static final long RESET_PASSWORD_TOKEN_TTL_SECONDS = 600;

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

    /** Set by single-device logout to blacklist exactly the access_token in use for that
     *  request — Gateway checks this per request (05-cookie-auth-flow.md E.4/E.6). TTL = time
     *  remaining until that token's own `exp`, set at the call site (JwtRevocationService),
     *  never the fixed access-token TTL — a token nearing expiry doesn't need a fresh 15 min. */
    public static String jwtBlacklist(String jti) {
        return "cache:jwt_blacklist:" + jti;
    }

    /** Set by logout-all/admin-block to invalidate every access_token already issued to this
     *  user at once — Gateway rejects any JWT whose `iat` is <= this value. TTL = the
     *  access-token TTL (JwtProperties.accessTokenTtlSeconds), set at the call site: past that
     *  point every pre-existing JWT has expired on its own anyway, so the key can safely
     *  disappear. See 05-cookie-auth-flow.md E.6. */
    public static String jwtRevokedBefore(UUID userId) {
        return "cache:jwt_revoked_before:" + userId;
    }


    public static String resetPasswordToken(String resetToken) {
        return "cache:reset_password_token:" + resetToken;
    }


    public static String pendingRegister(String target) {
        return "cache:pending_register:" + target;
    }


    public static String loginFailCount(UUID userId) {
        return "cache:login_fail_count:" + userId;
    }

    public static String loginLockout(UUID userId) {
        return "cache:login_lockout:" + userId;
    }

    private RedisKeys() {
    }
}
