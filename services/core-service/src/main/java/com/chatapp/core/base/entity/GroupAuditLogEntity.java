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
@Table(name = "group_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupAuditLogEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupAuditLogEntity(UUID groupId, UUID actorId, String action, UUID targetUserId, String oldValue, String newValue) {
        this.groupId = groupId;
        this.actorId = actorId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}
