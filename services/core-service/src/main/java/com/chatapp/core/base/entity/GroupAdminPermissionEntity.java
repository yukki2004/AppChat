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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "group_admin_permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupAdminPermissionEntity {

    @EmbeddedId
    private GroupAdminPermissionId id;

    @Column(name = "can_approve_members", nullable = false)
    private boolean canApproveMembers;

    @Column(name = "can_kick_members", nullable = false)
    private boolean canKickMembers;

    @Column(name = "can_edit_group_info", nullable = false)
    private boolean canEditGroupInfo;

    @Column(name = "can_manage_events", nullable = false)
    private boolean canManageEvents;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GroupAdminPermissionEntity(UUID groupId, UUID userId) {
        this.id = new GroupAdminPermissionId(groupId, userId);
        this.canApproveMembers = true;
        this.canKickMembers = true;
        this.canEditGroupInfo = true;
        this.canManageEvents = true;
    }

    public void update(boolean canApproveMembers, boolean canKickMembers, boolean canEditGroupInfo, boolean canManageEvents) {
        this.canApproveMembers = canApproveMembers;
        this.canKickMembers = canKickMembers;
        this.canEditGroupInfo = canEditGroupInfo;
        this.canManageEvents = canManageEvents;
    }
}
