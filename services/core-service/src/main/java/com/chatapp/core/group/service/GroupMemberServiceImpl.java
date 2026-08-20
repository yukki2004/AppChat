package com.chatapp.core.group.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.constant.RabbitConstant;
import com.chatapp.core.base.entity.GroupAdminPermissionEntity;
import com.chatapp.core.base.entity.GroupAdminPermissionId;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.entity.GroupMemberNicknameEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.message.group.GroupMemberRemovedMessage;
import com.chatapp.core.base.message.group.GroupRoleChangedMessage;
import com.chatapp.core.base.repository.GroupAdminPermissionRepository;
import com.chatapp.core.base.repository.GroupMemberNicknameRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.GroupMemberService;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.dto.response.GroupMemberListResponse;
import com.chatapp.core.group.dto.response.GroupMemberSummaryResponse;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

    /** Always page at this size, regardless of group size — groups can grow well past the
     *  default 500-member cap (a group can be upgraded to a higher cap later), so there's no
     *  member-count threshold below which returning everything in 1 call is safe long-term. A
     *  group smaller than this still gets everything in 1 call (page.size() <= limit), just
     *  without a special case for it. */
    private static final int DEFAULT_PAGE_SIZE = 40;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAdminPermissionRepository groupAdminPermissionRepository;
    private final GroupMemberNicknameRepository groupMemberNicknameRepository;
    private final UserRepository userRepository;
    private final GroupPermissionResolver groupPermissionResolver;
    private final GroupMembershipMutator groupMembershipMutator;
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
    @Transactional(readOnly = true)
    public GroupMemberListResponse listMembers(UUID actorId, UUID groupId, String cursor) {
        log.debug("listMembers start actorId={} groupId={} hasCursor={}", actorId, groupId, cursor != null);

        groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        groupPermissionResolver.requireActiveMember(groupId, actorId);

        Cursor decoded = cursor == null ? null : decodeCursor(cursor);
        List<GroupMemberEntity> page = groupMemberRepository.findPage(
                groupId,
                decoded == null ? null : decoded.joinedAt(),
                decoded == null ? null : decoded.id(),
                PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));
        boolean hasMore = page.size() > DEFAULT_PAGE_SIZE;
        List<GroupMemberEntity> pageMembers = hasMore ? page.subList(0, DEFAULT_PAGE_SIZE) : page;

        List<UUID> userIds = pageMembers.stream().map(GroupMemberEntity::getUserId).toList();
        Map<UUID, UserEntity> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Map<UUID, String> nicknamesByUserId = groupMemberNicknameRepository
                .findByIdGroupIdAndIdUserIdIn(groupId, userIds).stream()
                .collect(Collectors.toMap(n -> n.getId().getUserId(), GroupMemberNicknameEntity::getNickname));

        List<GroupMemberSummaryResponse> summaries = pageMembers.stream()
                .map(member -> {
                    UserEntity user = usersById.get(member.getUserId());
                    String resolvedName = nicknamesByUserId.getOrDefault(
                            member.getUserId(), user == null ? null : user.getDisplayName());
                    return new GroupMemberSummaryResponse(
                            member.getUserId(),
                            resolvedName,
                            user == null ? null : user.getAvatarUrl(),
                            member.getRole());
                })
                .toList();
        String nextCursor = hasMore ? encodeCursor(pageMembers.get(pageMembers.size() - 1)) : null;

        log.info("listMembers success actorId={} groupId={} count={} hasMore={}",
                actorId, groupId, summaries.size(), hasMore);
        return new GroupMemberListResponse(summaries, nextCursor);
    }

    private static String encodeCursor(GroupMemberEntity member) {
        String raw = member.getJoinedAt().toEpochMilli() + "_" + member.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('_');
            Instant joinedAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator)));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return new Cursor(joinedAt, id);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new AppException(ErrorCode.GROUP_INVALID_CURSOR);
        }
    }

    private record Cursor(Instant joinedAt, UUID id) {
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

        groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requireOwner(actorMembership);
        if (newOwnerUserId.equals(actorId)) {
            throw new AppException(ErrorCode.GROUP_SELF_TRANSFER_NOT_ALLOWED);
        }
        GroupMemberEntity targetMembership = requireActiveTargetMember(groupId, newOwnerUserId);

        // Old Owner drops straight to MEMBER — no group_admin_permissions row inserted here.
        // If the new Owner wants to keep them as an Admin, that's a separate promoteToAdmin()
        // call (their own decision, own permission baseline), not something transferOwnership
        // assumes for them.
        actorMembership.changeRole(GroupMemberRole.MEMBER);
        groupMemberRepository.saveAndFlush(actorMembership);

        targetMembership.changeRole(GroupMemberRole.OWNER);
        try {
            groupMemberRepository.saveAndFlush(targetMembership);
        } catch (DataIntegrityViolationException raceLost) {
            throw new AppException(ErrorCode.GROUP_CONCURRENT_MODIFICATION);
        }
        groupAdminPermissionRepository.deleteById(new GroupAdminPermissionId(groupId, newOwnerUserId));

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupRoleChangedMessage(actorId, GroupMemberRole.MEMBER, actorId));
        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_ROLE_CHANGED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupRoleChangedMessage(newOwnerUserId, GroupMemberRole.OWNER, actorId));

        log.info("transferOwnership success actorId={} groupId={} newOwnerUserId={}", actorId, groupId, newOwnerUserId);
    }

    @Override
    @Transactional
    public void leave(UUID actorId, UUID groupId) {
        log.debug("leave start actorId={} groupId={}", actorId, groupId);

        GroupEntity group = groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity membership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        if (membership.getRole() == GroupMemberRole.OWNER) {
            throw new AppException(ErrorCode.GROUP_OWNER_CANNOT_LEAVE);
        }

        membership.leave();
        groupMemberRepository.save(membership);
        group.decrementMemberCount();
        groupRepository.save(group);
        if (membership.getRole() == GroupMemberRole.ADMIN) {
            groupAdminPermissionRepository.deleteById(new GroupAdminPermissionId(groupId, actorId));
        }

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_MEMBER_REMOVED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_MEMBER_REMOVED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupMemberRemovedMessage(actorId, null));

        log.info("leave success actorId={} groupId={}", actorId, groupId);
    }

    private GroupMemberEntity requireActiveTargetMember(UUID groupId, UUID targetUserId) {
        return groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_TARGET_NOT_A_MEMBER));
    }
}
