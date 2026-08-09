package com.chatapp.core.twofactor.otp;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.config.OtpProperties;
import com.chatapp.core.base.constant.OtpPurpose;
import com.chatapp.core.base.constant.RedisKeys;
import com.chatapp.core.base.entity.OtpCodeEntity;
import com.chatapp.core.base.repository.OtpCodeRepository;
import com.chatapp.core.base.util.HashUtils;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCodeService {

    private static final int CODE_LENGTH = 6;

    private final OtpCodeRepository otpCodeRepository;
    private final OtpProperties otpProperties;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();



    /** Cheap pre-check for callers about to do expensive work (BCrypt hashing, DB uniqueness
     *  checks, Redis writes) before actually generating a code — see
     *  AuthServiceImpl#registerStart/linkIdentifierStart, which call this FIRST, before any of
     *  that work, so a spam request gets rejected practically for free instead of paying the
     *  full cost of a real attempt just to be rejected at the last step. generate() below still
     *  re-checks both conditions itself (defense-in-depth against races and other callers that
     *  don't pre-check). */
    public void requireNotOnCooldown(String target, OtpPurpose purpose) {
        if (isLocked(target, purpose)) {
            log.warn("otp pre-check rejected target={} purpose={} reason=LOCKED_OUT", target, purpose);
            throw new AppException(ErrorCode.OTP_LOCKED);
        }
        if (isResendTooSoon(target, purpose)) {
            log.warn("otp pre-check rejected target={} purpose={} reason=RESEND_TOO_SOON", target, purpose);
            throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON);
        }
    }

    @Transactional
    public String generate(String target, OtpPurpose purpose, UUID userId) {
        log.debug("otp generate start target={} purpose={} userId={}", target, purpose, userId);
        requireNotOnCooldown(target, purpose);

        List<OtpCodeEntity> stale = otpCodeRepository.findByTargetAndPurposeAndUserIdAndUsedAtIsNull(target, purpose, userId);
        if (!stale.isEmpty()) {
            log.debug("otp generate invalidating {} stale code(s) target={} purpose={}", stale.size(), target, purpose);
        }
        stale.forEach(OtpCodeEntity::invalidate);
        otpCodeRepository.saveAll(stale);

        String code = generateNumericCode();
        Instant expiresAt = Instant.now().plusSeconds(otpProperties.getCodeTtlSeconds());
        OtpCodeEntity entity = new OtpCodeEntity(
                target, HashUtils.sha256Hex(code), purpose, userId, expiresAt, otpProperties.getMaxAttempts());
        otpCodeRepository.save(entity);
        markResendCooldown(target, purpose);
        log.info("otp generate success target={} purpose={} userId={} expiresAt={}", target, purpose, userId, expiresAt);
        return code;
    }

    @Transactional
    public boolean verify(String target, OtpPurpose purpose, UUID userId, String code) {
        log.debug("otp verify start target={} purpose={} userId={}", target, purpose, userId);
        if (isLocked(target, purpose)) {
            log.warn("otp verify rejected target={} purpose={} reason=LOCKED_OUT", target, purpose);
            throw new AppException(ErrorCode.OTP_LOCKED);
        }

        Optional<OtpCodeEntity> found = otpCodeRepository
                .findTopByTargetAndPurposeAndUserIdAndUsedAtIsNullOrderByCreatedAtDesc(target, purpose, userId);
        if (found.isEmpty()) {
            log.warn("otp verify failed target={} purpose={} reason=NO_ACTIVE_CODE", target, purpose);
            return false;
        }

        OtpCodeEntity entity = found.get();
        if (entity.isExpired() || entity.attemptsExhausted()) {
            log.warn("otp verify failed target={} purpose={} reason={}", target, purpose,
                    entity.isExpired() ? "EXPIRED" : "ATTEMPTS_EXHAUSTED");
            return false;
        }

        if (!entity.getCodeHash().equals(HashUtils.sha256Hex(code))) {
            entity.recordFailedAttempt();
            otpCodeRepository.save(entity);
            if (entity.attemptsExhausted()) {
                lock(target, purpose);
                log.warn("otp verify failed target={} purpose={} reason=WRONG_CODE — attempts now exhausted, locking out", target, purpose);
            } else {
                log.warn("otp verify failed target={} purpose={} reason=WRONG_CODE", target, purpose);
            }
            return false;
        }

        entity.markUsed();
        otpCodeRepository.save(entity);
        log.info("otp verify success target={} purpose={} userId={}", target, purpose, userId);
        return true;
    }

    private boolean isLocked(String target, OtpPurpose purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.otpLockout(target, purpose)));
    }

    private boolean isResendTooSoon(String target, OtpPurpose purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.otpResendCooldown(target, purpose)));
    }

    private void markResendCooldown(String target, OtpPurpose purpose) {
        redisTemplate.opsForValue().set(
                RedisKeys.otpResendCooldown(target, purpose), "1",
                Duration.ofSeconds(otpProperties.getResendCooldownSeconds()));
    }

    private void lock(String target, OtpPurpose purpose) {
        redisTemplate.opsForValue().set(
                RedisKeys.otpLockout(target, purpose), "1",
                Duration.ofSeconds(otpProperties.getLockoutSeconds()));
    }

    private String generateNumericCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + CODE_LENGTH + "d", value);
    }

}
