package com.chatapp.core.auth.qrlogin;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.auth.AuthCookieBuilder;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginDeviceInfoResponse;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginInitResponse;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginStatusResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.base.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * QR code login (WhatsApp-style): a NEW, not-yet-authenticated device shows a QR encoding
 * {@code qrToken}; an ALREADY-logged-in device scans it and confirms — that confirmation is
 * what substitutes for 2FA on the new device (see AuthService#issueTokensForDevice). Real-time
 * delivery of the approval to the new device is a WS push via realtime-gateway, not polling —
 * GET .../status here is a REST fallback/bootstrap only, see QrLoginStatusResponse javadoc.
 */
@RestController
@RequiredArgsConstructor
public class QrLoginController {

    private final QrLoginService qrLoginService;
    private final AuthCookieBuilder cookieBuilder;

    @PostMapping("/auth/qr-login/init")
    public ResponseEntity<ApiResponse<QrLoginInitResponse>> init(
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
        return ResponseEntity.ok(ApiResponse.ok(qrLoginService.init(clientIp, userAgent)));
    }

    @GetMapping("/auth/qr-login/{qrToken}/status")
    public ResponseEntity<ApiResponse<QrLoginStatusResponse>> status(@PathVariable String qrToken) {
        return ResponseEntity.ok(ApiResponse.ok(qrLoginService.getStatus(qrToken)));
    }

    @GetMapping("/auth/qr-login/{qrToken}/device-info")
    public ResponseEntity<ApiResponse<QrLoginDeviceInfoResponse>> deviceInfo(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String qrToken) {
        return ResponseEntity.ok(ApiResponse.ok(qrLoginService.getDeviceInfo(qrToken, userId)));
    }

    @PostMapping("/auth/qr-login/{qrToken}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String qrToken) {
        qrLoginService.confirm(qrToken, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/auth/qr-login/{qrToken}/claim")
    public ResponseEntity<ApiResponse<?>> claim(@PathVariable String qrToken) {
        AuthResult result = qrLoginService.claim(qrToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieBuilder.buildAccessRefreshCookies(result))
                .body(ApiResponse.ok(result.user()));
    }
}
