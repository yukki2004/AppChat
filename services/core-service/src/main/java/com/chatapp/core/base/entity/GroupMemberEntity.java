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

import com.chatapp.core.base.constant.GroupMemberRole;

@Entity
@Table(name = "group_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMemberEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupMemberRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public GroupMemberEntity(UUID groupId, UUID userId, GroupMemberRole role, UUID addedBy) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
        this.addedBy = addedBy;
        this.isActive = true;
    }

    public void changeRole(GroupMemberRole role) {
        this.role = role;
    }

    public void leave() {
        this.isActive = false;
        this.leftAt = Instant.now();
    }
}
