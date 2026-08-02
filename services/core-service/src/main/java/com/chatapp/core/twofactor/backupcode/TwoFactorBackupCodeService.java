package com.chatapp.core.twofactor.backupcode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Base32;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.entity.TwoFactorBackupCodeEntity;
import com.chatapp.core.base.repository.TwoFactorBackupCodeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Account-level backup codes — one set of 8 shared across every 2FA method the user has
 * enabled (see the `two_factor_backup_codes` table comment in
 * `docs/.../services/03-core-service.md`). Only the hash is ever persisted; plaintext codes
 * are returned once, at generation time, and never stored anywhere.
 */
@Component
@RequiredArgsConstructor
public class TwoFactorBackupCodeService {

    private static final int CODE_COUNT = 8;
    private static final int CODE_BYTE_LENGTH = 5; // 5 bytes -> 8 base32 chars, formatted XXXX-XXXX
    private static final int MAX_CONSUME_RETRIES = 3;

    private final TwoFactorBackupCodeRepository backupCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Lazy-insert: only generates when this is the user's first 2FA method (no row yet).
     *  Returns empty when a set already exists — callers use that to decide whether the
     *  confirm response should include a freshly generated batch or not. */
    @Transactional
    public Optional<List<String>> generateIfAbsent(UUID userId) {
        if (backupCodeRepository.existsById(userId)) {
            return Optional.empty();
        }
        List<String> plainCodes = generatePlainCodes();
        backupCodeRepository.save(new TwoFactorBackupCodeEntity(userId, hashAll(plainCodes)));
        return Optional.of(plainCodes);
    }

    /** Always generates a fresh set of 8 and overwrites the existing one — every old code
     *  (used or not) stops working immediately. Caller is responsible for re-auth (password)
     *  before calling this, same as disabling a 2FA method. */
    @Transactional
    public List<String> regenerate(UUID userId) {
        List<String> plainCodes = generatePlainCodes();
        TwoFactorBackupCodeEntity entity = backupCodeRepository.findById(userId)
                .orElseGet(() -> new TwoFactorBackupCodeEntity(userId, List.of()));
        entity.replaceWith(hashAll(plainCodes));
        backupCodeRepository.save(entity);
        return plainCodes;
    }

    /** Consumes the code on success (one-time use) — matches the plaintext against every
     *  remaining hash since backup codes aren't ordered like OTP (no "latest" one to check).
     *
     *  Retries on {@link OptimisticLockingFailureException}: 2 devices submitting different
     *  backup codes for the same user at nearly the same instant both read the same array
     *  version, so whichever saves second would otherwise get its update silently discarded
     *  by the first (lost update — the code it just consumed would still show as unused).
     *  Re-reading and retrying is safe here because each attempt re-checks {@code matches}
     *  against the freshly-read array, so a code already consumed by the other request will
     *  correctly fail on retry rather than being consumed twice. */
    @Transactional
    public boolean verify(UUID userId, String code) {
        String hash = sha256Hex(normalize(code));

        for (int attempt = 0; attempt < MAX_CONSUME_RETRIES; attempt++) {
            Optional<TwoFactorBackupCodeEntity> found = backupCodeRepository.findById(userId);
            if (found.isEmpty()) {
                return false;
            }
            TwoFactorBackupCodeEntity entity = found.get();
            if (!entity.matches(hash)) {
                return false;
            }
            entity.consume(hash);
            try {
                backupCodeRepository.saveAndFlush(entity);
                return true;
            } catch (OptimisticLockingFailureException e) {
                // Another concurrent verify won the race for this row — retry against the
                // now-current array instead of losing this consume silently.
            }
        }
        throw new IllegalStateException(
                "Could not verify backup code for user " + userId + " after " + MAX_CONSUME_RETRIES + " retries");
    }

    public boolean hasBackupCodes(UUID userId) {
        return backupCodeRepository.existsById(userId);
    }

    private List<String> generatePlainCodes() {
        return Stream.generate(this::generateOneCode).limit(CODE_COUNT).toList();
    }

    private String generateOneCode() {
        byte[] raw = new byte[CODE_BYTE_LENGTH];
        secureRandom.nextBytes(raw);
        String base32 = new Base32().encodeToString(raw).replace("=", "");
        return base32.substring(0, 4) + "-" + base32.substring(4, 8);
    }

    private List<String> hashAll(List<String> plainCodes) {
        return plainCodes.stream().map(c -> sha256Hex(normalize(c))).toList();
    }

    /** Accepts the code with or without the display dash, case-insensitive. */
    private String normalize(String code) {
        return code.replace("-", "").toUpperCase();
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
