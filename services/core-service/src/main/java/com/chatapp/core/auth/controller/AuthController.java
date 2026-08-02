package com.chatapp.core.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.request.TwoFactorChallengeRequest;
import com.chatapp.core.auth.dto.request.TwoFactorSubmitRequest;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.dto.response.TwoFactorRequiredResponse;
import com.chatapp.core.auth.dto.response.UserResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.auth.result.TwoFactorChallengeResult;
import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.exception.InvalidCredentialsException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Register/login flow (email/phone + password, no OAuth2 yet) plus the 2-step 2FA
 * challenge/submit flow. Cookies: access_token Path=/, refresh_token Path=/auth/refresh,
 * pre_auth_token Path=/auth/login/2fa — all HttpOnly+Secure+SameSite=Strict, same mechanism,
 * never a token in the response body. Response body is always wrapped in {@link ApiResponse}.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final String PRE_AUTH_COOKIE_NAME = "pre_auth_token";
    private static final String PRE_AUTH_COOKIE_PATH = "/auth/login/2fa";

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

    private void requirePreAuthToken(String preAuthToken) {
        if (preAuthToken == null || preAuthToken.isBlank()) {
            throw new InvalidCredentialsException("Missing pre_auth_token");
        }
    }

    private String[] buildAccessRefreshCookies(AuthResult result) {
        ResponseCookie accessCookie = buildCookie("access_token", result.accessToken(), "/", result.accessTokenTtlSeconds());
        ResponseCookie refreshCookie = buildCookie("refresh_token", result.refreshToken(), "/auth/refresh", result.refreshTokenTtlSeconds());
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
