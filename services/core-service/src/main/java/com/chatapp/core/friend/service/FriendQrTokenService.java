package com.chatapp.core.friend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class FriendQrTokenService {

    public static final long TTL_SECONDS = RedisKeys.FRIEND_QR_TOKEN_TTL_SECONDS;

    public static final long MAX_USES = 30;

    private final StringRedisTemplate redisTemplate;

    public Result create(UUID userId) {
        String activeKey = RedisKeys.friendQrActive(userId);
        String previousToken = redisTemplate.opsForValue().get(activeKey);
        if (previousToken != null) {
            killToken(previousToken);
        }

        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(TTL_SECONDS);
        redisTemplate.opsForValue().set(RedisKeys.friendQrToken(token), userId.toString(), ttl);
        redisTemplate.opsForValue().set(activeKey, token, ttl);
        return new Result(token, Instant.now().plusSeconds(TTL_SECONDS));
    }

    public Optional<UUID> resolve(String token) {
        String value = redisTemplate.opsForValue().get(RedisKeys.friendQrToken(token));
        return Optional.ofNullable(value).map(UUID::fromString);
    }


    public void recordUse(String token) {
        String usesKey = RedisKeys.friendQrTokenUses(token);
        Long count = redisTemplate.opsForValue().increment(usesKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(usesKey, Duration.ofSeconds(TTL_SECONDS));
        }
        if (count != null && count > MAX_USES) {
            throw new AppException(ErrorCode.FRIEND_QR_TOKEN_USAGE_LIMIT_REACHED);
        }
    }

    public void revoke(UUID userId) {
        String activeKey = RedisKeys.friendQrActive(userId);
        String token = redisTemplate.opsForValue().get(activeKey);
        if (token != null) {
            killToken(token);
            redisTemplate.delete(activeKey);
        }
    }

    private void killToken(String token) {
        redisTemplate.delete(RedisKeys.friendQrToken(token));
        redisTemplate.delete(RedisKeys.friendQrTokenUses(token));
    }

    public record Result(String token, Instant expiresAt) {
    }
}
