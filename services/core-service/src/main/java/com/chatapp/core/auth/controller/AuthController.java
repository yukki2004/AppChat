package com.chatapp.core.auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.auth.AuthCookieBuilder;
import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.dto.request.ChangePasswordRequest;
import com.chatapp.core.auth.dto.request.ForgotPasswordRequest;
import com.chatapp.core.auth.dto.request.ForgotPasswordVerifyRequest;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.request.ResetPasswordRequest;
import com.chatapp.core.auth.dto.request.SetPasswordRequest;
import com.chatapp.core.auth.dto.request.TwoFactorChallengeRequest;
import com.chatapp.core.auth.dto.request.TwoFactorSubmitRequest;
import com.chatapp.core.auth.dto.response.SessionResponse;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.dto.response.TwoFactorRequiredResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.auth.result.TwoFactorChallengeResult;
import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.constant.RedisKeys;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Register/login flow (email/phone + password) plus the 2-step 2FA challenge/submit flow.
 * OAuth2 login (Google/Facebook/Apple) is a separate controller, see auth/oauth/OAuthController.
 * Cookies: access_token Path=/, refresh_token Path=/auth (covers
 * /auth/refresh, /auth/logout, /auth/logout-all — see REFRESH_COOKIE_PATH),
 * pre_auth_token Path=/auth/login/2fa — all HttpOnly+Secure+SameSite=Strict, same mechanism,
 * never a token in the response body. Response body is always wrapped in {@link ApiResponse}.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final String PRE_AUTH_COOKIE_NAME = "pre_auth_token";
    private static final String PRE_AUTH_COOKIE_PATH = "/auth/login/2fa";

    /** NOT "/auth/refresh" — RFC 6265 path matching is prefix-based, and "/auth/refresh" is not
     *  a prefix of "/auth/logout"/"/auth/logout-all", so the browser would silently never send
     *  this cookie to those endpoints. "/auth" is the narrowest prefix that covers refresh,
     *  logout, and logout-all all at once while still being HttpOnly and never reaching
     *  non-auth endpoints. */
    private static final String REFRESH_COOKIE_PATH = AuthCookieBuilder.REFRESH_COOKIE_PATH;

    private static final String RESET_TOKEN_COOKIE_NAME = "reset_token";
    private static final String RESET_TOKEN_COOKIE_PATH = "/auth/password";

    private final AuthService authService;
    private final AuthCookieBuilder cookieBuilder;

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request,
                                                  @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                                  @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        LoginOutcome outcome = authService.login(request, clientIp, userAgent);

        if (outcome instanceof TwoFactorChallengeResult challenge) {
            ResponseCookie preAuthCookie = cookieBuilder.buildCookie(
                    PRE_AUTH_COOKIE_NAME, challenge.preAuthToken(), PRE_AUTH_COOKIE_PATH, challenge.preAuthTokenTtlSeconds());
            TwoFactorRequiredResponse body = new TwoFactorRequiredResponse(true, challenge.availableMethods());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, preAuthCookie.toString())
                    .body(ApiResponse.ok(body));
        }

        AuthResult result = (AuthResult) outcome;
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildAccessRefreshCookies(result))
                .body(ApiResponse.ok(result.user()));
    }

    @PostMapping("/auth/login/2fa/challenge")
    public ResponseEntity<ApiResponse<TwoFactorChallengeAckResponse>> challengeTwoFactor(
            @Valid @RequestBody TwoFactorChallengeRequest request,
            @CookieValue(name = PRE_AUTH_COOKIE_NAME, required = false) String preAuthToken) {
        requirePreAuthToken(preAuthToken);
        TwoFactorChallengeAckResponse ack = authService.challengeTwoFactor(preAuthToken, request.getMethod());
        return ResponseEntity.ok(ApiResponse.ok(ack));
    }

    @PostMapping("/auth/login/2fa")
    public ResponseEntity<ApiResponse<UserResponse>> submitTwoFactor(
            @Valid @RequestBody TwoFactorSubmitRequest request,
            @CookieValue(name = PRE_AUTH_COOKIE_NAME, required = false) String preAuthToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        requirePreAuthToken(preAuthToken);
        AuthResult result = authService.verifyTwoFactor(
                preAuthToken, request.getCode(), clientIp, userAgent);

        ResponseCookie clearPreAuthCookie = ResponseCookie.from(PRE_AUTH_COOKIE_NAME, "")
                .httpOnly(true).secure(true).sameSite("Strict").path(PRE_AUTH_COOKIE_PATH).maxAge(0).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildAccessRefreshCookies(result))
                .header(HttpHeaders.SET_COOKIE, clearPreAuthCookie.toString())
                .body(ApiResponse.ok(result.user()));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<UserResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        AuthResult result = authService.refreshToken(refreshToken, clientIp, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildAccessRefreshCookies(result))
                .body(ApiResponse.ok(result.user()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @CookieValue(name = "access_token", required = false) String accessToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        authService.logout(refreshToken, accessToken, clientIp, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildCookie("access_token", "", "/", 0).toString())
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildCookie("refresh_token", "", REFRESH_COOKIE_PATH, 0).toString())
                .body(ApiResponse.ok());
    }

    /** Lists every active session for the caller — used by the client to render a device list
     *  and know which sessionId to pass to DELETE /auth/sessions/{sessionId}, instead of
     *  requiring a manual DB lookup. `is_current` is computed by comparing this request's own
     *  refresh_token cookie against each session's token_hash, not by any explicit session id
     *  the client sends. */
    @GetMapping("/auth/sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> listSessions(
            @RequestHeader("X-User-Id") UUID userId,
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        return ResponseEntity.ok(ApiResponse.ok(authService.listSessions(userId, refreshToken)));
    }

    /** Logs out 1 OTHER device — see AuthService#logoutSession for why its access_token can't
     *  be blacklisted immediately here. */
    @DeleteMapping("/auth/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> logoutSession(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        authService.logoutSession(userId, sessionId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/auth/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(name = "keep_current", defaultValue = "false") boolean keepCurrent,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        authService.logoutAll(userId, refreshToken, keepCurrent, clientIp, userAgent);
        if (keepCurrent) {
            return ResponseEntity.ok(ApiResponse.ok());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildCookie("access_token", "", "/", 0).toString())
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildCookie("refresh_token", "", REFRESH_COOKIE_PATH, 0).toString())
                .body(ApiResponse.ok());
    }


    @PutMapping("/auth/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        authService.changePassword(userId, request.getOldPassword(), request.getNewPassword(), clientIp, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildCookie("access_token", "", "/", 0).toString())
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildCookie("refresh_token", "", REFRESH_COOKIE_PATH, 0).toString())
                .body(ApiResponse.ok());
    }

    /** First-time password for an OAuth-only account — see AuthService#setPassword. Not for
     *  changing an existing password (that's PUT /auth/password, requires oldPassword). */
    @PostMapping("/auth/password/set")
    public ResponseEntity<ApiResponse<Void>> setPassword(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SetPasswordRequest request) {
        authService.setPassword(userId, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/auth/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok());
    }


    @PostMapping("/auth/password/forgot/verify")
    public ResponseEntity<ApiResponse<Void>> verifyForgotPassword(@Valid @RequestBody ForgotPasswordVerifyRequest request) {
        String resetToken = authService.verifyPasswordResetOtp(request.getEmail(), request.getCode());
        ResponseCookie cookie = cookieBuilder.buildCookie(
                RESET_TOKEN_COOKIE_NAME, resetToken, RESET_TOKEN_COOKIE_PATH, RedisKeys.RESET_PASSWORD_TOKEN_TTL_SECONDS);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/auth/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @CookieValue(name = RESET_TOKEN_COOKIE_NAME, required = false) String resetToken,
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(resetToken, request.getNewPassword());
        ResponseCookie clearResetToken = cookieBuilder.buildCookie(RESET_TOKEN_COOKIE_NAME, "", RESET_TOKEN_COOKIE_PATH, 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearResetToken.toString())
                .body(ApiResponse.ok());
    }

    private void requirePreAuthToken(String preAuthToken) {
        if (preAuthToken == null || preAuthToken.isBlank()) {
            throw new AppException(ErrorCode.PRE_AUTH_TOKEN_INVALID);
        }
    }

}
