package com.chatapp.core.auth.qrlogin.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import tools.jackson.databind.ObjectMapper;


@Component
@RequiredArgsConstructor
public class QrLoginSessionService {

    public static final long TTL_SECONDS = RedisKeys.QR_LOGIN_TTL_SECONDS;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public String create(String newDeviceIp, String newDeviceUserAgent) {
        String token = UUID.randomUUID().toString();
        String value = objectMapper.writeValueAsString(
                new State(Status.PENDING, newDeviceIp, newDeviceUserAgent, null));
        redisTemplate.opsForValue().set(RedisKeys.qrLogin(token), value, Duration.ofSeconds(TTL_SECONDS));
        return token;
    }

    public Optional<State> getState(String token) {
        return read(token);
    }


    @SneakyThrows
    public void approve(String token, UUID approvedByUserId) {
        String key = RedisKeys.qrLogin(token);
        Long remainingTtl = redisTemplate.getExpire(key);
        State current = read(token).orElseThrow(() -> new IllegalStateException("qr_login session not found: " + token));
        String value = objectMapper.writeValueAsString(
                new State(Status.APPROVED, current.newDeviceIp(), current.newDeviceUserAgent(), approvedByUserId));
        if (remainingTtl != null && remainingTtl > 0) {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(remainingTtl));
        } else {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(TTL_SECONDS));
        }
    }


    public void delete(String token) {
        redisTemplate.delete(RedisKeys.qrLogin(token));
    }

    @SneakyThrows
    private Optional<State> read(String token) {
        String value = redisTemplate.opsForValue().get(RedisKeys.qrLogin(token));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(value, State.class));
    }

    public enum Status {
        PENDING, APPROVED
    }

    public record State(Status status, String newDeviceIp, String newDeviceUserAgent, UUID approvedByUserId) {
    }
}
