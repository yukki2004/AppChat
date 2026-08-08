package com.chatapp.core.auth.oauth.strategy;

import com.chatapp.core.auth.oauth.dto.OAuthUserInfo;
import com.chatapp.core.base.constant.OAuthProvider;

/** 1 implementation per provider (GoogleOAuthStrategy now, FacebookOAuthStrategy/
 *  AppleOAuthStrategy later) — each hides its own token-exchange/verification API shape behind
 *  {@link #exchangeCode(String)}. Spring auto-collects every bean of this type into
 *  {@link OAuthProviderDispatcher}; adding a new provider never touches the dispatcher, the
 *  controller, or the service. */
public interface OAuthProviderStrategy {

    OAuthProvider provider();
    OAuthUserInfo exchangeCode(String code);
}
