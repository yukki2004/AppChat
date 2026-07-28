package com.chatapp.core.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Luồng đăng ký/đăng nhập cơ bản (email/password, CHƯA có OAuth2/2FA — xem
 * `services/core-service/CLAUDE.md`). Cookie đặt theo đúng `docs/.../05-cookie-auth-flow.md`
 * mục E.1/E.3: access_token Path=/, refresh_token Path=/auth/refresh, cả 2 HttpOnly+Secure+
 * SameSite=Strict.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.cookie-domain:}")
    private String cookieDomain;

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                                @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                                HttpServletRequest httpRequest) {
        AuthResult result = authService.login(request, httpRequest.getRemoteAddr(), userAgent);

        ResponseCookie accessCookie = buildCookie("access_token", result.accessToken(), "/", result.accessTokenTtlSeconds());
        ResponseCookie refreshCookie = buildCookie("refresh_token", result.refreshToken(), "/auth/refresh", result.refreshTokenTtlSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.user());
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
