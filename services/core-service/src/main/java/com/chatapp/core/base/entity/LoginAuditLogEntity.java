package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.chatapp.core.base.constant.LoginAuditEventType;

/**
 * Append-only security trail — rows are never updated/deleted by the app (only by the
 * archival/retention job described in `docs/.../services/03-core-service.md`). `userId` is
 * nullable: a LOGIN_FAILED for an unrecognized username/email/phone has no user to attach to.
 */
@Entity
@Table(name = "login_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAuditLogEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private LoginAuditEventType eventType;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public LoginAuditLogEntity(UUID userId, LoginAuditEventType eventType, String ipAddress, String userAgent,
                                String deviceId, UUID sessionId, String failureReason) {
        this.userId = userId;
        this.eventType = eventType;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
        this.sessionId = sessionId;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }
}
