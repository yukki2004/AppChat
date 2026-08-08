package com.chatapp.core.auth;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.SessionResponse;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.base.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginOutcome login(LoginRequest request, String ipAddress, String userAgent);

    TwoFactorChallengeAckResponse challengeTwoFactor(String preAuthToken, String methodName);

    AuthResult verifyTwoFactor(String preAuthToken, String code, String ipAddress, String userAgent);

    AuthResult refreshToken(String refreshToken, String ipAddress, String userAgent);

    void logout(String refreshToken, String accessToken, String ipAddress, String userAgent);

    List<SessionResponse> listSessions(UUID userId, String currentRefreshToken);

    void logoutSession(UUID userId, UUID sessionId, String ipAddress, String userAgent);

    void logoutAll(UUID userId, String currentRefreshToken, boolean keepCurrent, String ipAddress, String userAgent);

    void changePassword(UUID userId, String oldPassword, String newPassword, String ipAddress, String userAgent);

    void forgotPassword(String email);

    String verifyPasswordResetOtp(String email, String code);

    void resetPassword(String resetToken, String newPassword);
}
