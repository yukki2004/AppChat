package com.chatapp.core.auth.oauth;

import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.base.constant.OAuthProvider;

public interface OAuthService {
    LoginOutcome loginWithCallback(OAuthProvider provider, String code, String ipAddress, String userAgent);
}
