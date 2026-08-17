package com.chatapp.core.group.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.GroupMemberService;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.util.GroupPermissionResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupPermissionResolver groupPermissionResolver;
    private final GroupMembershipMutator groupMembershipMutator;

    @Override
    @Transactional
    public AddMemberResponse addMember(UUID actorId, UUID groupId, UUID targetUserId) {
        log.debug("addMember start actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);

        GroupEntity group = groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);

        userRepository.findByIdAndDeletedAtIsNull(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId)) {
            throw new AppException(ErrorCode.GROUP_ALREADY_MEMBER);
        }

        // TODO: check who_can_add_to_group (gRPC GetPrivacySettings) before adding — no such
        // client exists yet in this codebase (same gap as GroupServiceImpl#createGroup), so every
        // target is treated as addable for now.
        boolean actorBypassesApproval = actorMembership.getRole() != GroupMemberRole.MEMBER;
        if (!group.isRequireApproval() || actorBypassesApproval) {
            groupMembershipMutator.addMemberDirectly(group, targetUserId, GroupMemberRole.MEMBER, actorId);
            log.info("addMember success actorId={} groupId={} targetUserId={} outcome=ADDED", actorId, groupId, targetUserId);
            return new AddMemberResponse(groupId, targetUserId, GroupJoinOutcome.JOINED);
        }

        groupMembershipMutator.createOrReuseJoinRequest(groupId, targetUserId, actorId);
        log.info("addMember success actorId={} groupId={} targetUserId={} outcome=PENDING_APPROVAL",
                actorId, groupId, targetUserId);
        return new AddMemberResponse(groupId, targetUserId, GroupJoinOutcome.PENDING_APPROVAL);
    }
}
