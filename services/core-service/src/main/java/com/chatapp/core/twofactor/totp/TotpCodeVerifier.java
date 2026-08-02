package com.chatapp.core.twofactor.totp;

import java.security.InvalidKeyException;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

/** Shared RFC 6238 check — used both at login (TotpTwoFactorStrategy) and when enabling TOTP
 *  (TwoFactorSettingsService.confirmTotp), same secret format (Base32) both times. */
@Component
public class TotpCodeVerifier {

    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator();

    public boolean verify(String base32Secret, String code) {
        try {
            SecretKeySpec key = new SecretKeySpec(new Base32().decode(base32Secret), totp.getAlgorithm());
            int expected = totp.generateOneTimePassword(key, Instant.now());
            String formatted = String.format("%0" + totp.getPasswordLength() + "d", expected);
            return formatted.equals(code);
        } catch (InvalidKeyException e) {
            return false;
        }
    }
}
