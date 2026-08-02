package com.chatapp.core.twofactor.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.twofactor.dto.request.DisableTwoFactorRequest;
import com.chatapp.core.twofactor.dto.request.RegenerateBackupCodesRequest;
import com.chatapp.core.twofactor.dto.request.TwoFactorConfirmRequest;
import com.chatapp.core.twofactor.dto.response.BackupCodesResponse;
import com.chatapp.core.twofactor.dto.response.TotpSetupResponse;
import com.chatapp.core.twofactor.service.TwoFactorSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/2fa")
@RequiredArgsConstructor
public class TwoFactorSettingsController {

    private final TwoFactorSettingsService twoFactorSettingsService;

    @PostMapping("/totp/setup")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setupTotp(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(twoFactorSettingsService.setupTotp(userId)));
    }

    @PostMapping("/totp/confirm")
    public ResponseEntity<ApiResponse<BackupCodesResponse>> confirmTotp(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody TwoFactorConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(twoFactorSettingsService.confirmTotp(userId, request.getCode())));
    }

    @PostMapping("/email/setup")
    public ResponseEntity<ApiResponse<Void>> setupEmail(@RequestHeader("X-User-Id") UUID userId) {
        twoFactorSettingsService.setupEmail(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/email/confirm")
    public ResponseEntity<ApiResponse<BackupCodesResponse>> confirmEmail(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody TwoFactorConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(twoFactorSettingsService.confirmEmail(userId, request.getCode())));
    }

    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<ApiResponse<BackupCodesResponse>> regenerateBackupCodes(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody RegenerateBackupCodesRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(twoFactorSettingsService.regenerateBackupCodes(userId, request.getPassword())));
    }

    @DeleteMapping("/{method}")
    public ResponseEntity<ApiResponse<Void>> disableMethod(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String method,
            @Valid @RequestBody DisableTwoFactorRequest request) {
        twoFactorSettingsService.disableMethod(userId, method, request.getPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
