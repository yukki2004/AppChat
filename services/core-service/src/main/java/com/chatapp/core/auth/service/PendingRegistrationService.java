package com.chatapp.core.auth.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;

import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Backs `cache:pending_register:{target}` (Redis-only, no DB) — see the register flow in
 * AuthServiceImpl. Holds the not-yet-created account's fields between POST /auth/register/otp
 * and POST /auth/register/verify, keyed by the email/phone being verified since no user_id
 * exists until verification succeeds.
 */
@Component
@RequiredArgsConstructor
public class PendingRegistrationService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void save(String target, PendingRegistration data, long ttlSeconds) {
        String value = objectMapper.writeValueAsString(data);
        redisTemplate.opsForValue().set(RedisKeys.pendingRegister(target), value, Duration.ofSeconds(ttlSeconds));
    }

    @SneakyThrows
    public Optional<PendingRegistration> get(String target) {
        String value = redisTemplate.opsForValue().get(RedisKeys.pendingRegister(target));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(value, PendingRegistration.class));
    }

    public void delete(String target) {
        redisTemplate.delete(RedisKeys.pendingRegister(target));
    }
}
