package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Stores the refresh_token — NEVER the raw value, only token_hash = SHA-256(refresh_token).
 */
@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSessionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    private String platform;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "login_country", length = 2)
    private String loginCountry;

    @Column(name = "login_city")
    private String loginCity;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason")
    private String revokeReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserSessionEntity(String tokenHash, UUID userId, String ipAddress, String userAgent,
                              String loginCountry, String loginCity, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.loginCountry = loginCountry;
        this.loginCity = loginCity;
        this.expiresAt = expiresAt;
        this.isActive = true;
        Instant now = Instant.now();
        this.lastActiveAt = now;
        this.createdAt = now;
    }

    public void revoke(String reason) {
        this.isActive = false;
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
    }
}
