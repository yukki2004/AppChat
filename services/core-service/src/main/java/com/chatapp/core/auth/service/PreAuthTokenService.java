package com.chatapp.core.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;

import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Backs `cache:pre_auth:{token}` (Redis-only, no DB — see the login flow in AuthServiceImpl).
 * Value starts as {@code {user_id}} right after password verification, then gains
 * {@code method} once the client picks one via /auth/login/2fa/challenge — same token, TTL
 * preserved across that update (never reset back to the full 5 minutes).
 *
 * {@code method} is stored as a raw string, not {@link com.chatapp.core.base.constant.TwoFactorMethod}
 * — AuthServiceImpl also accepts the pseudo-method "BACKUP_CODE" here, which isn't a real
 * {@code two_factor_methods} row/enum value (backup codes are account-level, see
 * TwoFactorBackupCodeService), so this class can't be tied to that enum's type.
 */
@Component
@RequiredArgsConstructor
public class PreAuthTokenService {

    public static final long TTL_SECONDS = RedisKeys.PRE_AUTH_TOKEN_TTL_SECONDS;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public String create(UUID userId) {
        String token = UUID.randomUUID().toString();
        String value = objectMapper.writeValueAsString(new State(userId, null));
        redisTemplate.opsForValue().set(RedisKeys.preAuth(token), value, Duration.ofSeconds(TTL_SECONDS));
        return token;
    }

    public Optional<UUID> getUserId(String token) {
        return read(token).map(State::userId);
    }

    public Optional<String> getMethod(String token) {
        return read(token).map(State::method).filter(m -> m != null);
    }

    @SneakyThrows
    public void setMethod(String token, String method) {
        String key = RedisKeys.preAuth(token);
        Long remainingTtl = redisTemplate.getExpire(key);
        State current = read(token).orElseThrow(() -> new IllegalStateException("pre_auth_token not found: " + token));
        String value = objectMapper.writeValueAsString(new State(current.userId(), method));
        if (remainingTtl != null && remainingTtl > 0) {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(remainingTtl));
        } else {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(TTL_SECONDS));
        }
    }

    public void delete(String token) {
        redisTemplate.delete(RedisKeys.preAuth(token));
    }

    @SneakyThrows
    private Optional<State> read(String token) {
        String value = redisTemplate.opsForValue().get(RedisKeys.preAuth(token));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(value, State.class));
    }

    private record State(UUID userId, String method) {
    }
}
