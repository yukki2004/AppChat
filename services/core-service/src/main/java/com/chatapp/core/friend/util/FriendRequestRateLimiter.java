package com.chatapp.core.friend.util;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.RedisKeys;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/** Pure Redis anti-abuse checks for {@code POST /friends/requests} (mục #9b,
 *  `docs/.../03-core-service.md`) — no dependency on {@link com.chatapp.core.base.entity.FriendshipEntity}
 *  or any repository, kept separate from the request-resolution logic in
 *  {@link FriendRequestResolver} on purpose. */
@Component
@RequiredArgsConstructor
public class FriendRequestRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public void checkRateLimit(UUID requesterId) {
        String key = RedisKeys.rateLimitFriendRequest(requesterId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(RedisKeys.FRIEND_REQUEST_RATE_LIMIT_TTL_SECONDS));
        }
        if (count != null && count > RedisKeys.FRIEND_REQUEST_DAILY_LIMIT) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_RATE_LIMIT_EXCEEDED);
        }
    }

    /** Not called yet — disabled by product decision, see FriendServiceImpl.sendRequest(). Kept
     *  rather than deleted in case this gets revisited. */
    public void checkCooldown(UUID requesterId, UUID addresseeId) {
        Boolean onCooldown = redisTemplate.hasKey(RedisKeys.friendRequestCooldown(requesterId, addresseeId));
        if (Boolean.TRUE.equals(onCooldown)) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_COOLDOWN);
        }
    }
}
