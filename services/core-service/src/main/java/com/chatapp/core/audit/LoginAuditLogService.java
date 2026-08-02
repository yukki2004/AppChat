package com.chatapp.core.audit;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.LoginAuditEventType;
import com.chatapp.core.base.entity.LoginAuditLogEntity;
import com.chatapp.core.base.repository.LoginAuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Single explicit call site for every `login_audit_logs` write (function #29 in
 * `docs/.../services/03-core-service.md`) — called directly from whichever service performs
 * the action (AuthServiceImpl today), same style as OtpCodeService/TwoFactorBackupCodeService
 * rather than an AOP aspect or event listener: this codebase doesn't use annotation-driven
 * side effects anywhere else, and the fields that matter differ enough per event type
 * (LOGIN_FAILED has no user_id yet, PASSWORD_CHANGE has no ip/device) that a generic
 * interceptor would need almost as much per-call-site logic anyway.
 *
 * No {@code @Transactional} here on purpose — runs inside whatever transaction the caller is
 * already in (e.g. AuthServiceImpl.login), so a LOGIN_SUCCESS row and its `user_sessions` row
 * commit or roll back together.
 */
@Component
@RequiredArgsConstructor
public class LoginAuditLogService {

    private final LoginAuditLogRepository loginAuditLogRepository;

    public void record(UUID userId, LoginAuditEventType eventType, String ipAddress, String userAgent,
                        String deviceId, UUID sessionId, String failureReason) {
        loginAuditLogRepository.save(
                new LoginAuditLogEntity(userId, eventType, ipAddress, userAgent, deviceId, sessionId, failureReason));
    }
}
