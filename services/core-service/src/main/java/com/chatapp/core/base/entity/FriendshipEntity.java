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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.chatapp.core.base.constant.FriendshipStatus;

/**
 * Exactly 1 row per unordered pair of users, enforced by a DB unique index on
 * {@code (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))} — see
 * migration `V20260804090000__create_friendships_table.sql`. A row is never deleted while
 * PENDING/REJECTED/CANCELLED, only reused: sending a request again after REJECTED/CANCELLED
 * updates this same row (see {@link #resendAs}) instead of inserting a new one, which would
 * violate that unique index.
 */
@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendshipEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Column(nullable = false)
    private FriendshipStatus status;

    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FriendshipEntity(UUID requesterId, UUID addresseeId, String message) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.status = FriendshipStatus.PENDING;
        this.message = message;
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public void reject() {
        this.status = FriendshipStatus.REJECTED;
    }

    public void cancel() {
        this.status = FriendshipStatus.CANCELLED;
    }

    /** Reuses this row for a fresh request after REJECTED/CANCELLED — direction may flip
     *  (the other person can now be the requester) since the unordered-pair unique index
     *  doesn't care which side is which. */
    public void resendAs(UUID requesterId, UUID addresseeId, String message) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.status = FriendshipStatus.PENDING;
        this.message = message;
    }
}
