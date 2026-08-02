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

import com.chatapp.core.base.constant.OtpPurpose;

/** Stores only code_hash = SHA-256(code) — same one-way-hash rule as refresh_token, never the
 *  plaintext code. */
@Entity
@Table(name = "otp_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpCodeEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String target;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private OtpPurpose purpose;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OtpCodeEntity(String target, String codeHash, OtpPurpose purpose, UUID userId, Instant expiresAt, int maxAttempts) {
        this.target = target;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.maxAttempts = maxAttempts;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean attemptsExhausted() {
        return attemptCount >= maxAttempts;
    }

    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    /** Same DB effect as {@link #markUsed()} (sets `used_at`, both are filtered out by
     *  `usedAtIsNull` queries) — kept as a separate method purely for readability at the call
     *  site: this means "superseded by a newer code", not "the user successfully verified it". */
    public void invalidate() {
        this.usedAt = Instant.now();
    }
}
