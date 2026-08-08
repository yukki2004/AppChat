package com.chatapp.core.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.chatapp.core.auth.result.AuthResult;


@Component
public class AuthCookieBuilder {

    public static final String REFRESH_COOKIE_PATH = "/auth";

    @Value("${app.cookie-domain:}")
    private String cookieDomain;

    public String[] buildAccessRefreshCookies(AuthResult result) {
        ResponseCookie accessCookie = buildCookie("access_token", result.accessToken(), "/", result.accessTokenTtlSeconds());
        ResponseCookie refreshCookie = buildCookie("refresh_token", result.refreshToken(), REFRESH_COOKIE_PATH, result.refreshTokenTtlSeconds());
        return new String[] {accessCookie.toString(), refreshCookie.toString()};
    }

    public ResponseCookie buildCookie(String name, String value, String path, long maxAgeSeconds) {
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
