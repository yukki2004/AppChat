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

import com.chatapp.core.base.constant.GroupJoinRequestStatus;

@Entity
@Table(name = "group_join_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupJoinRequestEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private GroupJoinRequestStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupJoinRequestEntity(UUID groupId, UUID userId, UUID invitedBy) {
        this.groupId = groupId;
        this.userId = userId;
        this.invitedBy = invitedBy;
        this.status = GroupJoinRequestStatus.PENDING;
    }

    public void approve(UUID reviewerId) {
        this.status = GroupJoinRequestStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewerId, String reason) {
        this.status = GroupJoinRequestStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.rejectReason = reason;
    }

    public void cancel() {
        this.status = GroupJoinRequestStatus.CANCELLED;
    }
}
