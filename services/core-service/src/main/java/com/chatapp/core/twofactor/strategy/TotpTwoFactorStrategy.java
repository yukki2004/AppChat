package com.chatapp.core.twofactor.strategy;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.twofactor.totp.TotpCodeVerifier;
import com.chatapp.core.twofactor.totp.TotpSecretCipher;

import lombok.RequiredArgsConstructor;

/** Nothing is sent for TOTP — the user already has the code generator (authenticator app). */
@Component
@RequiredArgsConstructor
public class TotpTwoFactorStrategy implements TwoFactorChallengeStrategy {

    private final TotpSecretCipher totpSecretCipher;
    private final TotpCodeVerifier totpCodeVerifier;

    @Override
    public TwoFactorMethod method() {
        return TwoFactorMethod.TOTP;
    }

    @Override
    public void challenge(UserEntity user, TwoFactorMethodEntity config) {
        // No-op by design: nothing to send, the code is generated locally by the user's app.
    }

    @Override
    public boolean verify(UserEntity user, TwoFactorMethodEntity config, String code) {
        String base32Secret = totpSecretCipher.decrypt(config.getTotpSecretEnc());
        return totpCodeVerifier.verify(base32Secret, code);
    }
}
