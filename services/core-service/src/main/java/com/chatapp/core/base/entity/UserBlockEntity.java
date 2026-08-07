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

/**
 * Directional — a row here means {@code blockerId} blocked {@code blockedId}, nothing about the
 * reverse. See migration `V20260805100000__create_user_blocks_table.sql` for the unique index on
 * the ordered pair.
 *
 * <p>TODO (product decision, not yet built): every row here is currently a "full" block — wipes
 * friendship/close-friend, blocks friend requests, and (once other services enforce it) hides
 * profile/search/presence. A narrower "message/call-only" block (stays friends, still visible,
 * only mutes messaging + calls) has been discussed but not scheduled. If it's picked up, this
 * entity needs a {@code scope} column (SMALLINT enum, {@code FULL}/{@code MESSAGE_CALL_ONLY} —
 * see skills/naming-conventions.md #3), and every caller of this table needs to filter by scope:
 * see the TODOs in {@code BlockServiceImpl} and {@code docs/.../03-core-service.md} mục 3.1 #24.
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

    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserBlockEntity(UUID blockerId, UUID blockedId, String reason) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.reason = reason;
    }
}
