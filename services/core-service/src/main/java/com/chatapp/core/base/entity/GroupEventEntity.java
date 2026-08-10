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

@Entity
@Table(name = "group_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String location;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "event_timezone", nullable = false, length = 50)
    private String eventTimezone;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "reminder_minutes_before", nullable = false)
    private int reminderMinutesBefore;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupEventEntity(UUID groupId, String title, String location, Instant eventTime,
            String eventTimezone, UUID createdBy, int reminderMinutesBefore) {
        this.groupId = groupId;
        this.title = title;
        this.location = location;
        this.eventTime = eventTime;
        this.eventTimezone = eventTimezone;
        this.createdBy = createdBy;
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.isCancelled = false;
    }

    public void cancel() {
        this.isCancelled = true;
    }

    public void markReminded() {
        this.remindedAt = Instant.now();
    }
}
