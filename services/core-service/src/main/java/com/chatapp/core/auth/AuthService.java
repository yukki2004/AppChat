package com.chatapp.core.auth;

import java.util.UUID;

import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.dto.response.UserResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginOutcome login(LoginRequest request, String ipAddress, String userAgent);

    TwoFactorChallengeAckResponse challengeTwoFactor(String preAuthToken, String methodName);

    AuthResult verifyTwoFactor(String preAuthToken, String code, String ipAddress, String userAgent);

    void logout(String refreshToken, String accessToken, String ipAddress, String userAgent);

    void logoutSession(UUID userId, UUID sessionId, String ipAddress, String userAgent);

    void logoutAll(UUID userId, String currentRefreshToken, boolean keepCurrent, String ipAddress, String userAgent);
}
