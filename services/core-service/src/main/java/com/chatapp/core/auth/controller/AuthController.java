package com.chatapp.core.auth.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.request.TwoFactorChallengeRequest;
import com.chatapp.core.auth.dto.request.TwoFactorSubmitRequest;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.dto.response.TwoFactorRequiredResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.auth.result.TwoFactorChallengeResult;
import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.base.UserResponse;
import com.chatapp.core.exception.InvalidCredentialsException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Register/login flow (email/phone + password, no OAuth2 yet) plus the 2-step 2FA
 * challenge/submit flow. Cookies: access_token Path=/, refresh_token Path=/auth (covers
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
    private static final String REFRESH_COOKIE_PATH = "/auth";

    private final AuthService authService;

    @Value("${app.cookie-domain:}")
    private String cookieDomain;

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
            ResponseCookie preAuthCookie = buildCookie(
                    PRE_AUTH_COOKIE_NAME, challenge.preAuthToken(), PRE_AUTH_COOKIE_PATH, challenge.preAuthTokenTtlSeconds());
            TwoFactorRequiredResponse body = new TwoFactorRequiredResponse(true, challenge.availableMethods());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, preAuthCookie.toString())
                    .body(ApiResponse.ok(body));
        }

        AuthResult result = (AuthResult) outcome;
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessRefreshCookies(result))
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
                .header(HttpHeaders.SET_COOKIE, buildAccessRefreshCookies(result))
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
                .header(HttpHeaders.SET_COOKIE, buildAccessRefreshCookies(result))
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
                .header(HttpHeaders.SET_COOKIE, buildCookie("access_token", "", "/", 0).toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie("refresh_token", "", REFRESH_COOKIE_PATH, 0).toString())
                .body(ApiResponse.ok());
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
                .header(HttpHeaders.SET_COOKIE, buildCookie("access_token", "", "/", 0).toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie("refresh_token", "", REFRESH_COOKIE_PATH, 0).toString())
                .body(ApiResponse.ok());
    }

    private void requirePreAuthToken(String preAuthToken) {
        if (preAuthToken == null || preAuthToken.isBlank()) {
            throw new InvalidCredentialsException("Missing pre_auth_token");
        }
    }

    private String[] buildAccessRefreshCookies(AuthResult result) {
        ResponseCookie accessCookie = buildCookie("access_token", result.accessToken(), "/", result.accessTokenTtlSeconds());
        ResponseCookie refreshCookie = buildCookie("refresh_token", result.refreshToken(), REFRESH_COOKIE_PATH, result.refreshTokenTtlSeconds());
        return new String[] {accessCookie.toString(), refreshCookie.toString()};
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAgeSeconds);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }
}
