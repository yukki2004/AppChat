package com.chatapp.core.twofactor.strategy;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.OtpPurpose;
import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.twofactor.otp.OtpCodeService;
import com.chatapp.core.twofactor.otp.OtpMailSender;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailTwoFactorStrategy implements TwoFactorChallengeStrategy {

    private final OtpCodeService otpCodeService;
    private final OtpMailSender otpMailSender;

    @Override
    public TwoFactorMethod method() {
        return TwoFactorMethod.EMAIL;
    }

    @Override
    public void challenge(UserEntity user, TwoFactorMethodEntity config) {
        String code = otpCodeService.generate(user.getEmail(), OtpPurpose.LOGIN_2FA, user.getId());
        otpMailSender.send(user.getEmail(), code);
    }

    @Override
    public boolean verify(UserEntity user, TwoFactorMethodEntity config, String code) {
        return otpCodeService.verify(user.getEmail(), OtpPurpose.LOGIN_2FA, user.getId(), code);
    }
}
