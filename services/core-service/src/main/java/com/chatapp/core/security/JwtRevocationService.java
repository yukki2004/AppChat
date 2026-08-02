package com.chatapp.core.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;

import lombok.RequiredArgsConstructor;

/**
 * Writes the 2 Redis keys API Gateway/WS Gateway check on every request to make access_token
 * revocation take effect before its natural `exp` (05-cookie-auth-flow.md E.6) — Core Service
 * only ever writes here, the read/enforce side lives in Gateway, not here.
 */
@Component
@RequiredArgsConstructor
public class JwtRevocationService {

    private final StringRedisTemplate redisTemplate;

    /** Single-device logout — blacklists exactly the jti in use for that request. No-op if the
     *  token is already past its own `exp` (nothing left to blacklist). */
    public void blacklist(String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(RedisKeys.jwtBlacklist(jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    /** logout-all / admin-block — invalidates every access_token already issued to this user at
     *  once, regardless of jti, since none of those jti values are known here. */
    public void revokeAllForUser(UUID userId, long accessTokenTtlSeconds) {
        redisTemplate.opsForValue().set(
                RedisKeys.jwtRevokedBefore(userId), Instant.now().toString(), Duration.ofSeconds(accessTokenTtlSeconds));
    }
}
