package com.chatapp.core.auth.oauth.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatapp.core.auth.oauth.dto.OAuthUserInfo;
import com.chatapp.core.base.constant.OAuthProvider;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

/** Routes to the right {@link OAuthProviderStrategy} by provider — same shape as
 *  TwoFactorChallengeDispatcher. Adding Facebook/Apple later = add 1 new
 *  {@code @Component implements OAuthProviderStrategy}, nothing here changes. */
@Component
public class OAuthProviderDispatcher {

    private final Map<OAuthProvider, OAuthProviderStrategy> strategies;

    public OAuthProviderDispatcher(List<OAuthProviderStrategy> strategies) {
        this.strategies = new EnumMap<>(OAuthProvider.class);
        strategies.forEach(strategy -> this.strategies.put(strategy.provider(), strategy));
    }

    public OAuthUserInfo exchangeCode(OAuthProvider provider, String code) {
        return resolve(provider).exchangeCode(code);
    }

    private OAuthProviderStrategy resolve(OAuthProvider provider) {
        OAuthProviderStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new AppException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
        }
        return strategy;
    }
}
