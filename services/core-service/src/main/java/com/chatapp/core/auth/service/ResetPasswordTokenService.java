package com.chatapp.core.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;

import lombok.RequiredArgsConstructor;

/**
 * Backs `cache:reset_password_token:{token}` (Redis-only, no DB — same shape as
 * {@link PreAuthTokenService}). Value is just the plain `user_id`, minted once
 * /auth/password/forgot/verify confirms the OTP, consumed exactly once by /auth/password/reset.
 */
@Component
@RequiredArgsConstructor
public class ResetPasswordTokenService {

    private final StringRedisTemplate redisTemplate;

    public String create(UUID userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                RedisKeys.resetPasswordToken(token), userId.toString(),
                Duration.ofSeconds(RedisKeys.RESET_PASSWORD_TOKEN_TTL_SECONDS));
        return token;
    }

    public Optional<UUID> getUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(RedisKeys.resetPasswordToken(token));
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    public void delete(String token) {
        redisTemplate.delete(RedisKeys.resetPasswordToken(token));
    }
}
