package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

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

import com.chatapp.core.base.constant.TwoFactorMethod;

/**
 * One row per (user_id, method) — a user can have multiple 2FA methods registered at once
 * (TOTP + SMS + EMAIL). Existence of a row = that method is enabled; no "is_enabled" column
 * needed. No default rows for a newly registered user.
 */
@Entity
@Table(name = "two_factor_methods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwoFactorMethodEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TwoFactorMethod method;

    @Column(name = "totp_secret_enc")
    private String totpSecretEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TwoFactorMethodEntity(UUID userId, TwoFactorMethod method, String totpSecretEnc) {
        this.userId = userId;
        this.method = method;
        this.totpSecretEnc = totpSecretEnc;
        this.createdAt = Instant.now();
    }
}
