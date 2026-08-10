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
import org.hibernate.annotations.CreationTimestamp;

import com.chatapp.core.base.constant.BlockScope;

/**
 * Directional — a row here means {@code blockerId} blocked {@code blockedId}, nothing about the
 * reverse. See migration `V20260805100000__create_user_blocks_table.sql` for the unique index on
 * the ordered pair.
 *
 * <p>Decided (2026-08-09): block mutes messaging/calls only — does NOT touch
 * {@code friendships}/{@code close_friends}, does NOT block friend requests, does NOT hide the
 * profile. Enforcement lives in messaging-service/call-service (not built yet — see TODO in
 * {@code docs/.../03-core-service.md} #24). {@code scope} says WHICH of messaging/calls is muted
 * ({@link BlockScope#MESSAGE}/{@link BlockScope#CALL} alone, or {@link BlockScope#ALL} for both)
 * — v1 only ever produces {@code ALL}; picking just one is a future UI choice, not built yet.
 */
@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlockEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockScope scope;

    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserBlockEntity(UUID blockerId, UUID blockedId, String reason) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.reason = reason;
        this.scope = BlockScope.ALL;
    }
}
