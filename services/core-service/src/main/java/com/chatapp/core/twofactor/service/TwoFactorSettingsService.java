package com.chatapp.core.twofactor.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

import org.apache.commons.codec.binary.Base32;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.config.TotpProperties;
import com.chatapp.core.base.constant.OtpPurpose;
import com.chatapp.core.base.constant.RedisKeys;
import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.TwoFactorMethodRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.InvalidCredentialsException;
import com.chatapp.core.exception.TwoFactorMethodAlreadyEnabledException;
import com.chatapp.core.exception.TwoFactorMethodNotEnabledException;
import com.chatapp.core.twofactor.backupcode.TwoFactorBackupCodeService;
import com.chatapp.core.twofactor.dto.response.BackupCodesResponse;
import com.chatapp.core.twofactor.dto.response.TotpSetupResponse;
import com.chatapp.core.twofactor.otp.OtpCodeService;
import com.chatapp.core.twofactor.otp.OtpMailSender;
import com.chatapp.core.twofactor.totp.TotpCodeVerifier;
import com.chatapp.core.twofactor.totp.TotpSecretCipher;

import lombok.RequiredArgsConstructor;

/**
 * Enable/disable flow for TOTP/EMAIL — nothing is written to `two_factor_methods` on setup
 * alone, only after the user proves they actually configured it correctly (scanned the QR
 * into their app / received the emailed code). SMS is deliberately not implemented yet.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorSettingsService {

    private static final int TOTP_SECRET_BYTE_LENGTH = 20;

    private final UserRepository userRepository;
    private final TwoFactorMethodRepository twoFactorMethodRepository;
    private final OtpCodeService otpCodeService;
    private final OtpMailSender otpMailSender;
    private final TotpSecretCipher totpSecretCipher;
    private final TotpCodeVerifier totpCodeVerifier;
    private final TwoFactorBackupCodeService twoFactorBackupCodeService;
    private final TotpProperties totpProperties;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public TotpSetupResponse setupTotp(UUID userId) {
        UserEntity user = requireUser(userId);
        requireNotAlreadyEnabled(userId, TwoFactorMethod.TOTP);

        byte[] rawSecret = new byte[TOTP_SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(rawSecret);
        String base32Secret = new Base32().encodeToString(rawSecret);

        redisTemplate.opsForValue().set(
                RedisKeys.pendingTotpSecret(userId), base32Secret,
                Duration.ofSeconds(RedisKeys.PENDING_TOTP_SECRET_TTL_SECONDS));

        return new TotpSetupResponse(base32Secret, buildOtpAuthUri(user.getUsername(), base32Secret));
    }

    @Transactional
    public BackupCodesResponse confirmTotp(UUID userId, String code) {
        requireNotAlreadyEnabled(userId, TwoFactorMethod.TOTP);

        String pendingKey = RedisKeys.pendingTotpSecret(userId);
        String base32Secret = redisTemplate.opsForValue().get(pendingKey);
        if (base32Secret == null) {
            throw new InvalidCredentialsException("No pending TOTP setup — call /2fa/totp/setup first");
        }

        if (!totpCodeVerifier.verify(base32Secret, code)) {
            throw new InvalidCredentialsException("Invalid verification code");
        }

        twoFactorMethodRepository.save(
                new TwoFactorMethodEntity(userId, TwoFactorMethod.TOTP, totpSecretCipher.encrypt(base32Secret)));
        redisTemplate.delete(pendingKey);

        return new BackupCodesResponse(
                twoFactorBackupCodeService.generateIfAbsent(userId).orElse(null));
    }


    @Transactional
    public void setupEmail(UUID userId) {
        UserEntity user = requireUser(userId);
        requireNotAlreadyEnabled(userId, TwoFactorMethod.EMAIL);

        String code = otpCodeService.generate(user.getEmail(), OtpPurpose.ENABLE_2FA, userId);
        otpMailSender.send(user.getEmail(), code);
    }

    @Transactional
    public BackupCodesResponse confirmEmail(UUID userId, String code) {
        UserEntity user = requireUser(userId);
        requireNotAlreadyEnabled(userId, TwoFactorMethod.EMAIL);

        if (!otpCodeService.verify(user.getEmail(), OtpPurpose.ENABLE_2FA, userId, code)) {
            throw new InvalidCredentialsException("Invalid verification code");
        }

        twoFactorMethodRepository.save(new TwoFactorMethodEntity(userId, TwoFactorMethod.EMAIL, null));

        return new BackupCodesResponse(
                twoFactorBackupCodeService.generateIfAbsent(userId).orElse(null));
    }

    /** Re-auth via password (same reasoning as {@link #disableMethod}: a hijacked session
     *  shouldn't be able to mint itself a fresh long-lived fallback). Requires at least 1 2FA
     *  method enabled — backup codes only exist to back up a method that's actually on. */
    @Transactional
    public BackupCodesResponse regenerateBackupCodes(UUID userId, String password) {
        UserEntity user = requireUser(userId);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid password");
        }
        if (twoFactorMethodRepository.findByUserId(userId).isEmpty()) {
            throw new TwoFactorMethodNotEnabledException("No 2FA method is enabled for this account");
        }

        return new BackupCodesResponse(twoFactorBackupCodeService.regenerate(userId));
    }

    @Transactional
    public void disableMethod(UUID userId, String methodName, String password) {
        UserEntity user = requireUser(userId);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        TwoFactorMethod method = parseMethod(methodName);
        TwoFactorMethodEntity entity = twoFactorMethodRepository.findByUserId(userId).stream()
                .filter(m -> m.getMethod() == method)
                .findFirst()
                .orElseThrow(() -> new TwoFactorMethodNotEnabledException(method + " is not enabled for this account"));

        twoFactorMethodRepository.delete(entity);
        // TODO: publish a user.two_factor_disabled security alert (RoutingKeys.UserExchange)
        // once the outbox pattern is wired up for this service — see skills/outbox-pattern.md.
        // Disabling 2FA is a high-value target for account takeover, the account owner should
        // always be notified even if they didn't do it themselves.
    }

    private TwoFactorMethod parseMethod(String methodName) {
        try {
            return TwoFactorMethod.valueOf(methodName);
        } catch (IllegalArgumentException e) {
            throw new TwoFactorMethodNotEnabledException("Unknown 2FA method: " + methodName);
        }
    }

    private String buildOtpAuthUri(String username, String base32Secret) {
        String issuer = totpProperties.getIssuer();
        String label = URLEncoder.encode(issuer + ":" + username, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + issuer;
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }

    private void requireNotAlreadyEnabled(UUID userId, TwoFactorMethod method) {
        boolean alreadyEnabled = twoFactorMethodRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getMethod() == method);
        if (alreadyEnabled) {
            throw new TwoFactorMethodAlreadyEnabledException(method + " is already enabled for this account");
        }
    }
}
