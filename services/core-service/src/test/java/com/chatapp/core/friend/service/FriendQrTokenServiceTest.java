package com.chatapp.core.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.chatapp.core.base.constant.RedisKeys;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

/**
 * No Redis/Docker needed — {@link StringRedisTemplate} is a plain Mockito mock, same approach
 * as {@link com.chatapp.core.friend.service.FriendServiceImplTest} mocking JPA repositories.
 */
@ExtendWith(MockitoExtension.class)
class FriendQrTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private FriendQrTokenService qrTokenService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        qrTokenService = new FriendQrTokenService(redisTemplate);
    }

    @Test
    void create_storesTokenToUserMapping_andActivePointer_bothWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrActive(userId))).thenReturn(null);

        FriendQrTokenService.Result result = qrTokenService.create(userId);

        assertThat(result.token()).isNotBlank();
        Duration expectedTtl = Duration.ofSeconds(FriendQrTokenService.TTL_SECONDS);
        verify(valueOperations).set(RedisKeys.friendQrToken(result.token()), userId.toString(), expectedTtl);
        verify(valueOperations).set(RedisKeys.friendQrActive(userId), result.token(), expectedTtl);
    }

    @Test
    void create_killsPreviousActiveToken_whenOneAlreadyExists() {
        String oldToken = "old-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrActive(userId))).thenReturn(oldToken);

        qrTokenService.create(userId);

        verify(redisTemplate).delete(RedisKeys.friendQrToken(oldToken));
        verify(redisTemplate).delete(RedisKeys.friendQrTokenUses(oldToken));
    }

    @Test
    void create_doesNotTouchOldKeys_whenNoPreviousActiveToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrActive(userId))).thenReturn(null);

        qrTokenService.create(userId);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void resolve_returnsUserId_whenTokenExists() {
        String token = "some-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrToken(token))).thenReturn(userId.toString());

        Optional<UUID> result = qrTokenService.resolve(token);

        assertThat(result).contains(userId);
    }

    @Test
    void resolve_returnsEmpty_whenTokenMissingOrExpired() {
        String token = "missing-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrToken(token))).thenReturn(null);

        Optional<UUID> result = qrTokenService.resolve(token);

        assertThat(result).isEmpty();
    }

    @Test
    void recordUse_setsExpiry_onlyOnFirstUse() {
        String token = "some-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeys.friendQrTokenUses(token))).thenReturn(1L);

        qrTokenService.recordUse(token);

        verify(redisTemplate).expire(RedisKeys.friendQrTokenUses(token), Duration.ofSeconds(FriendQrTokenService.TTL_SECONDS));
    }

    @Test
    void recordUse_doesNotResetExpiry_onSubsequentUses() {
        String token = "some-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeys.friendQrTokenUses(token))).thenReturn(5L);

        qrTokenService.recordUse(token);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void recordUse_throwsUsageLimitReached_onceCountExceedsMax() {
        String token = "some-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeys.friendQrTokenUses(token))).thenReturn(FriendQrTokenService.MAX_USES + 1);

        assertThatThrownBy(() -> qrTokenService.recordUse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_QR_TOKEN_USAGE_LIMIT_REACHED);
    }

    @Test
    void recordUse_allowsExactlyMaxUses_withoutThrowing() {
        String token = "some-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeys.friendQrTokenUses(token))).thenReturn(FriendQrTokenService.MAX_USES);

        qrTokenService.recordUse(token);
        // no exception — reaching exactly MAX_USES is still allowed, only exceeding it is not.
    }

    @Test
    void revoke_deletesActiveTokenAndPointer_whenOneExists() {
        String token = "active-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrActive(userId))).thenReturn(token);

        qrTokenService.revoke(userId);

        verify(redisTemplate).delete(RedisKeys.friendQrToken(token));
        verify(redisTemplate).delete(RedisKeys.friendQrTokenUses(token));
        verify(redisTemplate).delete(RedisKeys.friendQrActive(userId));
    }

    @Test
    void revoke_isNoOp_whenNothingActive() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.friendQrActive(userId))).thenReturn(null);

        qrTokenService.revoke(userId);

        verify(redisTemplate, never()).delete(anyString());
    }
}
