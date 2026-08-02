package com.chatapp.core.twofactor.strategy;

import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;

public interface TwoFactorChallengeStrategy {

    TwoFactorMethod method();

    void challenge(UserEntity user, TwoFactorMethodEntity config);

    boolean verify(UserEntity user, TwoFactorMethodEntity config, String code);
}
