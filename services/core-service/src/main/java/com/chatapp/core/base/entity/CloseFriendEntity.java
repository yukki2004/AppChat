package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** {@code user_id} marked {@code friend_id} as a close friend — one-directional, like the
 *  underlying "close friends" product feature (#26): it does not imply the reverse. */
@Entity
@Table(name = "close_friends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CloseFriendEntity {

    @EmbeddedId
    private CloseFriendId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CloseFriendEntity(UUID userId, UUID friendId) {
        this.id = new CloseFriendId(userId, friendId);
    }
}
