package com.chatapp.core.auth.oauth.controller;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.auth.AuthCookieBuilder;
import com.chatapp.core.auth.dto.response.TwoFactorRequiredResponse;
import com.chatapp.core.auth.oauth.OAuthService;
import com.chatapp.core.auth.oauth.dto.request.OAuthCallbackRequest;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.auth.result.TwoFactorChallengeResult;
import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.base.constant.OAuthProvider;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OAuthController {

    private static final String PRE_AUTH_COOKIE_NAME = "pre_auth_token";
    private static final String PRE_AUTH_COOKIE_PATH = "/auth/login/2fa";

    private final OAuthService oauthService;
    private final AuthCookieBuilder cookieBuilder;

    @PostMapping("/auth/oauth/{provider}/callback")
    public ResponseEntity<ApiResponse<?>> callback(
            @PathVariable String provider,
            @Valid @RequestBody OAuthCallbackRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        OAuthProvider parsedProvider = parseProvider(provider);
        LoginOutcome outcome = oauthService.loginWithCallback(parsedProvider, request.getCode(), clientIp, userAgent);

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

    private OAuthProvider parseProvider(String provider) {
        try {
            return OAuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
        }
    }
}
