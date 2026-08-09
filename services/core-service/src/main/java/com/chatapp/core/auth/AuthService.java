package com.chatapp.core.auth;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.auth.dto.request.LinkIdentifierRequest;
import com.chatapp.core.auth.dto.request.LinkIdentifierVerifyRequest;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.request.RegisterVerifyRequest;
import com.chatapp.core.auth.dto.response.SessionResponse;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.base.entity.UserEntity;

public interface AuthService {

    /** Step 1 of register — validates uniqueness, stashes the pending account in Redis, sends
     *  an OTP to the one identifier provided. Does NOT create a user row yet. */
    void registerStart(RegisterRequest request);

    /** Step 2 of register — verifies the OTP, creates the user (identifier marked verified),
     *  and logs them in immediately (same shape as password login: 2FA can't be enabled yet on
     *  a brand new account, but reusing completeLogin keeps this consistent if that ever changes). */
    LoginOutcome registerVerify(RegisterVerifyRequest request, String ipAddress, String userAgent);

    /** Step 1 of linking a second identifier onto an existing account — re-auths via password,
     *  requires the target column (email/phone) currently NULL, sends OTP to the new target. */
    void linkIdentifierStart(UUID userId, LinkIdentifierRequest request);

    /** Step 2 of linking — verifies the OTP and writes the new email/phone as verified. */
    void linkIdentifierVerify(UUID userId, LinkIdentifierVerifyRequest request);

    LoginOutcome login(LoginRequest request, String ipAddress, String userAgent);

    LoginOutcome completeLogin(UserEntity user, String ipAddress, String userAgent);

    TwoFactorChallengeAckResponse challengeTwoFactor(String preAuthToken, String methodName);

    AuthResult verifyTwoFactor(String preAuthToken, String code, String ipAddress, String userAgent);

    AuthResult refreshToken(String refreshToken, String ipAddress, String userAgent);

    void logout(String refreshToken, String accessToken, String ipAddress, String userAgent);

    List<SessionResponse> listSessions(UUID userId, String currentRefreshToken);

    void logoutSession(UUID userId, UUID sessionId, String ipAddress, String userAgent);

    void logoutAll(UUID userId, String currentRefreshToken, boolean keepCurrent, String ipAddress, String userAgent);

    void changePassword(UUID userId, String oldPassword, String newPassword, String ipAddress, String userAgent);

    /** First-time password for an OAuth-only account (password_hash currently NULL) — see
     *  conversation decision: OAuth users must set a password before enabling 2FA, so
     *  disableMethod/regenerateBackupCodes (which re-auth via password) never lock them out. */
    void setPassword(UUID userId, String newPassword);

    void forgotPassword(String email);

    String verifyPasswordResetOtp(String email, String code);

    void resetPassword(String resetToken, String newPassword);
}
