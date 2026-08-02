package com.chatapp.core.twofactor.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
import com.chatapp.core.exception.OtpLockedException;

import lombok.RequiredArgsConstructor;

/**
 * Shared 6-digit OTP generate/verify logic — the same code path serves every
 * {@link OtpPurpose} (LOGIN_2FA today; REGISTER/RESET_PASSWORD/CHANGE_EMAIL/ENABLE_2FA later
 * reuse this unchanged, only `target`/`purpose` differ). Only the delivery channel (email vs
 * SMS) differs per caller — that's handled by each TwoFactorChallengeStrategy, not here.
 */
@Component
@RequiredArgsConstructor
public class OtpCodeService {

    private static final int CODE_LENGTH = 6;

    private final OtpCodeRepository otpCodeRepository;
    private final OtpProperties otpProperties;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Generates a new code, persists its hash, and returns the plaintext code to send —
     *  the plaintext is never itself persisted anywhere. Blocked while a lockout from a
     *  previous exhausted code is still active for this (target, purpose).
     *
     *  Invalidates every other still-active code for the same (target, purpose, userId)
     *  first, so at most 1 code is ever valid at a time. Without this, two concurrent
     *  requests (2 devices logging into the same account, or just clicking "resend") each
     *  create their own row, and {@code verify} only ever checks the newest one — the
     *  earlier, still-unexpired code silently stops working even though the user has it in
     *  hand, which just looks like a random "wrong code" bug. */
    @Transactional
    public String generate(String target, OtpPurpose purpose, UUID userId) {
        if (isLocked(target, purpose)) {
            throw new OtpLockedException(
                    "Too many failed attempts — try again in a few minutes");
        }

        List<OtpCodeEntity> stale = otpCodeRepository.findByTargetAndPurposeAndUserIdAndUsedAtIsNull(target, purpose, userId);
        stale.forEach(OtpCodeEntity::invalidate);
        otpCodeRepository.saveAll(stale);

        String code = generateNumericCode();
        Instant expiresAt = Instant.now().plusSeconds(otpProperties.getCodeTtlSeconds());
        OtpCodeEntity entity = new OtpCodeEntity(
                target, sha256Hex(code), purpose, userId, expiresAt, otpProperties.getMaxAttempts());
        otpCodeRepository.save(entity);
        return code;
    }

    /** Verifies against the latest unused code for (target, purpose, userId). Wrong code
     *  increments attempt_count; expired or attempts-exhausted rows never match. Exhausting
     *  the last attempt starts a lockout that also blocks {@link #generate}, not just this
     *  specific code — otherwise the caller could just request a fresh code and get 5 more
     *  tries immediately, making the attempt limit meaningless. */
    @Transactional
    public boolean verify(String target, OtpPurpose purpose, UUID userId, String code) {
        if (isLocked(target, purpose)) {
            throw new OtpLockedException(
                    "Too many failed attempts — try again in a few minutes");
        }

        Optional<OtpCodeEntity> found = otpCodeRepository
                .findTopByTargetAndPurposeAndUserIdAndUsedAtIsNullOrderByCreatedAtDesc(target, purpose, userId);
        if (found.isEmpty()) {
            return false;
        }

        OtpCodeEntity entity = found.get();
        if (entity.isExpired() || entity.attemptsExhausted()) {
            return false;
        }

        if (!entity.getCodeHash().equals(sha256Hex(code))) {
            entity.recordFailedAttempt();
            otpCodeRepository.save(entity);
            if (entity.attemptsExhausted()) {
                lock(target, purpose);
            }
            return false;
        }

        entity.markUsed();
        otpCodeRepository.save(entity);
        return true;
    }

    private boolean isLocked(String target, OtpPurpose purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.otpLockout(target, purpose)));
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

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
