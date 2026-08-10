package com.chatapp.core.base.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupEventRsvpId implements Serializable {

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "user_id")
    private UUID userId;

    public GroupEventRsvpId(UUID eventId, UUID userId) {
        this.eventId = eventId;
        this.userId = userId;
    }
}
