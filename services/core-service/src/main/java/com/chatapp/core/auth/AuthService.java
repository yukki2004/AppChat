package com.chatapp.core.auth;

import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.base.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginOutcome login(LoginRequest request, String ipAddress, String userAgent);

    TwoFactorChallengeAckResponse challengeTwoFactor(String preAuthToken, String methodName);

    AuthResult verifyTwoFactor(String preAuthToken, String code, String ipAddress, String userAgent);
}
