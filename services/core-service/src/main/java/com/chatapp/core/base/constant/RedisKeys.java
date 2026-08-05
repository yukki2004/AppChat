package com.chatapp.core.base.constant;

import java.util.UUID;

/** Every Redis key pattern + its TTL, together — one place, so key format and TTL can never
 *  drift out of sync with each other or get hand-typed twice at the call site. */
public final class RedisKeys {

    public static final long PRE_AUTH_TOKEN_TTL_SECONDS = 300;
    public static final long PENDING_TOTP_SECRET_TTL_SECONDS = 600;

    /** Mục #9b, `docs/.../03-core-service.md` — 50 lời mời kết bạn/ngày. */
    public static final int FRIEND_REQUEST_DAILY_LIMIT = 50;
    public static final long FRIEND_REQUEST_RATE_LIMIT_TTL_SECONDS = 86_400;
    public static final long FRIEND_REQUEST_COOLDOWN_TTL_SECONDS = 86_400;

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

    public static String rateLimitFriendRequest(UUID userId) {
        return "cache:rate_limit:friend_request:" + userId;
    }

    /** Directional — only blocks the original {@code requesterId} from re-sending to the same
     *  {@code addresseeId} who rejected them; the other direction is unaffected. Mục #9b,
     *  `docs/.../03-core-service.md`. */
    public static String friendRequestCooldown(UUID requesterId, UUID addresseeId) {
        return "cache:friend_request_cooldown:" + requesterId + ":" + addresseeId;
    }

    private RedisKeys() {
    }
}
