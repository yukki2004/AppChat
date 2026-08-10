package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.chatapp.core.base.constant.GroupEventRsvpStatus;

@Entity
@Table(name = "group_event_rsvp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupEventRsvpEntity {

    @EmbeddedId
    private GroupEventRsvpId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupEventRsvpStatus status;

    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;

    public GroupEventRsvpEntity(UUID eventId, UUID userId, GroupEventRsvpStatus status) {
        this.id = new GroupEventRsvpId(eventId, userId);
        this.status = status;
        this.respondedAt = Instant.now();
    }

    public void updateStatus(GroupEventRsvpStatus status) {
        this.status = status;
        this.respondedAt = Instant.now();
    }
}
