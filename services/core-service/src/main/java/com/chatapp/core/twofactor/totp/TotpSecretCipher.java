package com.chatapp.core.twofactor.totp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.config.TotpProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AES-256-GCM encrypt/decrypt for `two_factor_methods.totp_secret_enc`. Same key-handling
 * philosophy as JwtKeyManager: reads the key from config, but auto-generates an in-memory-only
 * key when missing so dev/local works out of the box — production MUST set
 * TOTP_SECRET_ENCRYPTION_KEY, or existing encrypted secrets become unreadable on every restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TotpSecretCipher {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final TotpProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey key;

    @PostConstruct
    public void init() throws GeneralSecurityException {
        if (properties.getSecretEncryptionKey() == null || properties.getSecretEncryptionKey().isBlank()) {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            this.key = generator.generateKey();
            log.warn("No TOTP_SECRET_ENCRYPTION_KEY set — generated a random in-memory key. "
                    + "Only acceptable for dev/local: every restart invalidates existing totp_secret_enc rows.");
        } else {
            byte[] decoded = Base64.getDecoder().decode(properties.getSecretEncryptionKey());
            this.key = new SecretKeySpec(decoded, "AES");
        }
    }

    public String encrypt(String plainBase32Secret) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainBase32Secret.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }
}
