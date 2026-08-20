package com.chatapp.core.group.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.constant.RabbitConstant;
import com.chatapp.core.base.entity.GroupAdminPermissionEntity;
import com.chatapp.core.base.entity.GroupAdminPermissionId;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.message.group.GroupMemberRemovedMessage;
import com.chatapp.core.base.message.group.GroupRoleChangedMessage;
import com.chatapp.core.base.repository.GroupAdminPermissionRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.GroupMemberService;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.util.GroupLockService;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAdminPermissionRepository groupAdminPermissionRepository;
    private final UserRepository userRepository;
    private final GroupPermissionResolver groupPermissionResolver;
    private final GroupMembershipMutator groupMembershipMutator;
    private final GroupLockService groupLockService;
    private final OutboxEventPublisher outboxEventPublisher;

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

    @Override
    @Transactional
    public void kickMember(UUID actorId, UUID groupId, UUID targetUserId) {
        log.debug("kickMember start actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);

        GroupEntity group = groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requirePermission(actorMembership, GroupPermissionAction.CAN_KICK_MEMBERS);
        GroupMemberEntity targetMembership = requireActiveTargetMember(groupId, targetUserId);

        boolean actorIsOwner = actorMembership.getRole() == GroupMemberRole.OWNER;
        boolean targetIsUnkickable = targetMembership.getRole() == GroupMemberRole.OWNER
                || (targetMembership.getRole() == GroupMemberRole.ADMIN && !actorIsOwner);
        if (targetIsUnkickable) {
            throw new AppException(ErrorCode.GROUP_CANNOT_KICK_OWNER_OR_ADMIN);
        }

        targetMembership.leave();
        groupMemberRepository.save(targetMembership);
        group.decrementMemberCount();
        groupRepository.save(group);
        if (targetMembership.getRole() == GroupMemberRole.ADMIN) {
            groupAdminPermissionRepository.deleteById(new GroupAdminPermissionId(groupId, targetUserId));
        }

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_MEMBER_REMOVED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_MEMBER_REMOVED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupMemberRemovedMessage(targetUserId, actorId));

        log.info("kickMember success actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);
    }

    @Override
    @Transactional
    public void promoteToAdmin(UUID actorId, UUID groupId, UUID targetUserId) {
        log.debug("promoteToAdmin start actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);

        groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requireOwner(actorMembership);
        GroupMemberEntity targetMembership = requireActiveTargetMember(groupId, targetUserId);
        if (targetMembership.getRole() != GroupMemberRole.MEMBER) {
            throw new AppException(ErrorCode.GROUP_TARGET_ALREADY_ADMIN);
        }

        targetMembership.changeRole(GroupMemberRole.ADMIN);
        groupMemberRepository.save(targetMembership);
        groupAdminPermissionRepository.save(new GroupAdminPermissionEntity(groupId, targetUserId));

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupRoleChangedMessage(targetUserId, GroupMemberRole.ADMIN, actorId));

        log.info("promoteToAdmin success actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);
    }

    @Override
    @Transactional
    public void demoteToMember(UUID actorId, UUID groupId, UUID targetUserId) {
        log.debug("demoteToMember start actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);

        groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requireOwner(actorMembership);
        GroupMemberEntity targetMembership = requireActiveTargetMember(groupId, targetUserId);
        if (targetMembership.getRole() != GroupMemberRole.ADMIN) {
            throw new AppException(ErrorCode.GROUP_TARGET_NOT_AN_ADMIN);
        }

        targetMembership.changeRole(GroupMemberRole.MEMBER);
        groupMemberRepository.save(targetMembership);
        groupAdminPermissionRepository.deleteById(new GroupAdminPermissionId(groupId, targetUserId));

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupRoleChangedMessage(targetUserId, GroupMemberRole.MEMBER, actorId));

        log.info("demoteToMember success actorId={} groupId={} targetUserId={}", actorId, groupId, targetUserId);
    }

    @Override
    @Transactional
    public void transferOwnership(UUID actorId, UUID groupId, UUID newOwnerUserId) {
        log.debug("transferOwnership start actorId={} groupId={} newOwnerUserId={}", actorId, groupId, newOwnerUserId);

        // Acquire before any read — see GroupLockService javadoc for the write-skew this closes
        // (2 concurrent transfers off the same Owner would otherwise both read "actor is OWNER"
        // before either commits, and both would go on to write a new Owner).
        groupLockService.tryLock(groupId);

        groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requireOwner(actorMembership);
        if (newOwnerUserId.equals(actorId)) {
            throw new AppException(ErrorCode.GROUP_SELF_TRANSFER_NOT_ALLOWED);
        }
        GroupMemberEntity targetMembership = requireActiveTargetMember(groupId, newOwnerUserId);

        actorMembership.changeRole(GroupMemberRole.ADMIN);
        groupMemberRepository.save(actorMembership);
        groupAdminPermissionRepository.save(new GroupAdminPermissionEntity(groupId, actorId));

        targetMembership.changeRole(GroupMemberRole.OWNER);
        groupMemberRepository.save(targetMembership);
        groupAdminPermissionRepository.deleteById(new GroupAdminPermissionId(groupId, newOwnerUserId));

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupRoleChangedMessage(actorId, GroupMemberRole.ADMIN, actorId));
        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupRoleChangedMessage(newOwnerUserId, GroupMemberRole.OWNER, actorId));

        log.info("transferOwnership success actorId={} groupId={} newOwnerUserId={}", actorId, groupId, newOwnerUserId);
    }

    private GroupMemberEntity requireActiveTargetMember(UUID groupId, UUID targetUserId) {
        return groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_TARGET_NOT_A_MEMBER));
    }
}
