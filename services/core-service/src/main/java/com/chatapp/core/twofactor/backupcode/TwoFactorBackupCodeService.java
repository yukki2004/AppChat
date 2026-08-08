package com.chatapp.core.twofactor.backupcode;

import java.security.SecureRandom;
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
import com.chatapp.core.base.util.HashUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoFactorBackupCodeService {

    private static final int CODE_COUNT = 8;
    private static final int CODE_BYTE_LENGTH = 5; // 5 bytes -> 8 base32 chars, formatted XXXX-XXXX
    private static final int MAX_CONSUME_RETRIES = 3;

    private final TwoFactorBackupCodeRepository backupCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();


    @Transactional
    public Optional<List<String>> generateIfAbsent(UUID userId) {
        log.debug("backupCodes generateIfAbsent start userId={}", userId);
        if (backupCodeRepository.existsById(userId)) {
            log.debug("backupCodes generateIfAbsent skipped userId={} reason=ALREADY_EXISTS", userId);
            return Optional.empty();
        }
        List<String> plainCodes = generatePlainCodes();
        backupCodeRepository.save(new TwoFactorBackupCodeEntity(userId, hashAll(plainCodes)));
        log.info("backupCodes generateIfAbsent success userId={} count={}", userId, plainCodes.size());
        return Optional.of(plainCodes);
    }

    @Transactional
    public List<String> regenerate(UUID userId) {
        log.debug("backupCodes regenerate start userId={}", userId);
        List<String> plainCodes = generatePlainCodes();
        TwoFactorBackupCodeEntity entity = backupCodeRepository.findById(userId)
                .orElseGet(() -> new TwoFactorBackupCodeEntity(userId, List.of()));
        entity.replaceWith(hashAll(plainCodes));
        backupCodeRepository.save(entity);
        log.info("backupCodes regenerate success userId={} count={}", userId, plainCodes.size());
        return plainCodes;
    }


    @Transactional
    public boolean verify(UUID userId, String code) {
        log.debug("backupCodes verify start userId={}", userId);
        String hash = HashUtils.sha256Hex(normalize(code));

        for (int attempt = 0; attempt < MAX_CONSUME_RETRIES; attempt++) {
            Optional<TwoFactorBackupCodeEntity> found = backupCodeRepository.findById(userId);
            if (found.isEmpty()) {
                log.warn("backupCodes verify failed userId={} reason=NO_CODES_ON_RECORD", userId);
                return false;
            }
            TwoFactorBackupCodeEntity entity = found.get();
            if (!entity.matches(hash)) {
                log.warn("backupCodes verify failed userId={} reason=WRONG_CODE", userId);
                return false;
            }
            entity.consume(hash);
            try {
                backupCodeRepository.saveAndFlush(entity);
                log.info("backupCodes verify success userId={} attempt={}", userId, attempt + 1);
                return true;
            } catch (OptimisticLockingFailureException e) {
                // Another concurrent verify won the race for this row — retry against the
                // now-current array instead of losing this consume silently.
                log.warn("backupCodes verify retrying userId={} attempt={} reason=CONCURRENT_UPDATE", userId, attempt + 1);
            }
        }
        log.error("backupCodes verify exhausted retries userId={} maxRetries={}", userId, MAX_CONSUME_RETRIES);
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
        return plainCodes.stream().map(c -> HashUtils.sha256Hex(normalize(c))).toList();
    }

    private String normalize(String code) {
        return code.replace("-", "").toUpperCase();
    }
}
