package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Account-level 2FA fallback, 1-1 with `users` — not tied to any single method (TOTP/SMS/EMAIL
 * all share this same set). A code is consumed by removing its hash from {@code codesHash} and
 * incrementing {@code codesUsed}, so the array length always equals {@code 8 - codesUsed} and
 * no separate per-code "used" flag is needed.
 */
@Entity
@Table(name = "two_factor_backup_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwoFactorBackupCodeEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "codes_hash", nullable = false)
    private List<String> codesHash;

    @Column(name = "codes_used", nullable = false)
    private int codesUsed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Guards against the lost-update race between 2 concurrent {@code consume} calls (2
     *  devices submitting different backup codes for the same user at once) — both would
     *  otherwise read the same array and the later {@code save} would silently overwrite the
     *  earlier one, resurrecting an already-used code. Hibernate rejects the losing UPDATE
     *  instead; the caller (TwoFactorBackupCodeService) retries by re-reading. */
    @Version
    @Column(nullable = false)
    private long version;

    public TwoFactorBackupCodeEntity(UUID userId, List<String> codesHash) {
        this.userId = userId;
        this.codesHash = codesHash;
        this.codesUsed = 0;
        this.updatedAt = Instant.now();
    }

    public void replaceWith(List<String> newCodesHash) {
        this.codesHash = newCodesHash;
        this.codesUsed = 0;
        this.updatedAt = Instant.now();
    }

    /** Removes the matching hash and increments {@code codesUsed}. Caller must have already
     *  found the hash in {@code codesHash} (see {@link #matches(String)}). */
    public void consume(String hash) {
        this.codesHash = codesHash.stream().filter(h -> !h.equals(hash)).toList();
        this.codesUsed++;
        this.updatedAt = Instant.now();
    }

    public boolean matches(String hash) {
        return codesHash.contains(hash);
    }
}
