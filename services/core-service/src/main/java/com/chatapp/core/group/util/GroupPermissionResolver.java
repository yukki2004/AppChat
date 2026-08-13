package com.chatapp.core.group.util;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupAdminPermissionEntity;
import com.chatapp.core.base.entity.GroupAdminPermissionId;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.repository.GroupAdminPermissionRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single place that decides "can role X do action Y in group Z" — every {@code group/} service
 * method (and the future gRPC {@code CheckGroupRole}) must go through here instead of scattering
 * its own role if-else, so the rule stays in exactly one spot to test and to change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPermissionResolver {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupAdminPermissionRepository groupAdminPermissionRepository;

    public GroupMemberEntity requireActiveMember(UUID groupId, UUID userId) {
        return groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_A_MEMBER));
    }

    public void requirePermission(GroupMemberEntity actorMembership, GroupPermissionAction action) {
        if (!canPerform(actorMembership, action)) {
            log.warn("group permission denied groupId={} userId={} role={} action={}",
                    actorMembership.getGroupId(), actorMembership.getUserId(), actorMembership.getRole(), action);
            throw new AppException(ErrorCode.GROUP_PERMISSION_DENIED);
        }
    }

    public boolean canPerform(GroupMemberEntity actorMembership, GroupPermissionAction action) {
        GroupMemberRole role = actorMembership.getRole();
        if (role == GroupMemberRole.OWNER) {
            return true;
        }
        if (role == GroupMemberRole.MEMBER) {
            return false;
        }

        GroupAdminPermissionId id = new GroupAdminPermissionId(actorMembership.getGroupId(), actorMembership.getUserId());
        GroupAdminPermissionEntity permission = groupAdminPermissionRepository.findById(id).orElse(null);
        if (permission == null) {
            // Every promotion to ADMIN inserts a full-true row (see #11) — a missing row here
            // means that insert never happened, not that the Admin should be locked out, so fail
            // open to the "Admin has full permission by default" baseline instead of surprising
            // every existing Admin with a sudden permission denial.
            return true;
        }
        return switch (action) {
            case CAN_APPROVE_MEMBERS -> permission.isCanApproveMembers();
            case CAN_KICK_MEMBERS -> permission.isCanKickMembers();
            case CAN_EDIT_GROUP_INFO -> permission.isCanEditGroupInfo();
            case CAN_MANAGE_EVENTS -> permission.isCanManageEvents();
        };
    }
}
