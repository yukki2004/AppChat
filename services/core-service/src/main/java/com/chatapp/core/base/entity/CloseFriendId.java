package com.chatapp.core.base.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite PK for {@link CloseFriendEntity} — {@code equals}/{@code hashCode} are required by
 *  JPA for any {@code @EmbeddedId} type. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CloseFriendId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "friend_id")
    private UUID friendId;

    public CloseFriendId(UUID userId, UUID friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }
}
