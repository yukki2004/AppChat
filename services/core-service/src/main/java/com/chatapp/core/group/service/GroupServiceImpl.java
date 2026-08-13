package com.chatapp.core.group.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.constant.RabbitConstant;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.message.group.GroupMemberJoinedMessage;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.GroupService;
import com.chatapp.core.group.dto.response.GroupInfoResponse;
import com.chatapp.core.group.dto.response.GroupInviteLinkResponse;
import com.chatapp.core.group.dto.response.GroupQrCodeResponse;
import com.chatapp.core.group.dto.response.GroupResponse;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private static final int MIN_OTHER_MEMBERS = 2;

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final GroupPermissionResolver groupPermissionResolver;

    @Override
    @Transactional
    public GroupResponse createGroup(
            UUID creatorId, String name, String avatarUrl, String description, List<UUID> memberIds) {
        userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Set<UUID> otherMemberIds = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        otherMemberIds.remove(creatorId);
        if (otherMemberIds.size() < MIN_OTHER_MEMBERS) {
            throw new AppException(ErrorCode.GROUP_MIN_MEMBERS_NOT_MET);
        }

        // TODO: gRPC MessagingService.CreateConversation once that client exists — conversation_ref
        // stays NULL until then, set via a follow-up update.
        GroupEntity group = new GroupEntity(name, avatarUrl, description, creatorId);
        groupRepository.save(group);

        GroupMemberEntity owner = new GroupMemberEntity(group.getId(), creatorId, GroupMemberRole.OWNER, null);
        groupMemberRepository.save(owner);
        group.incrementMemberCount();

        publishMemberJoined(group.getId(), creatorId, GroupMemberRole.OWNER);

        // TODO: check who_can_add_to_group (gRPC GetPrivacySettings) before inserting each member,
        // same as a regular add-member — no such client exists yet, so members go straight in.
        for (UUID memberId : otherMemberIds) {
            userRepository.findByIdAndDeletedAtIsNull(memberId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            GroupMemberEntity member = new GroupMemberEntity(group.getId(), memberId, GroupMemberRole.MEMBER, creatorId);
            groupMemberRepository.save(member);
            group.incrementMemberCount();
            publishMemberJoined(group.getId(), memberId, GroupMemberRole.MEMBER);
        }

        groupRepository.save(group);
        return GroupResponse.from(group);
    }

    @Override
    @Transactional
    public GroupInfoResponse updateInfo(UUID actorId, UUID groupId, String name, String description) {
        log.debug("updateInfo start actorId={} groupId={} hasName={} hasDescription={}",
                actorId, groupId, name != null, description != null);

        GroupEntity group = requireEditableGroup(actorId, groupId);

        if (name != null && !name.equals(group.getName())) {
            group.rename(name);
        }
        if (description != null && !description.equals(group.getDescription())) {
            group.changeDescription(description);
        }
        // TODO: avatar_url change is deferred until this codebase has a Media Service client —
        // per root CLAUDE.md rule #8 only media-service calls Cloudflare R2 directly, so the
        // client must upload there first and pass back the CDN URL for us to store as-is.

        groupRepository.save(group);
        log.info("updateInfo success actorId={} groupId={}", actorId, groupId);
        return GroupInfoResponse.from(group);
    }

    @Override
    @Transactional
    public GroupQrCodeResponse generateQrCode(UUID actorId, UUID groupId) {

        GroupEntity group = requireEditableGroup(actorId, groupId);
        group.rotateQrCode(UUID.randomUUID().toString());
        groupRepository.save(group);

        return new GroupQrCodeResponse(group.getQrCodeToken());
    }

    @Override
    @Transactional
    public GroupInviteLinkResponse generateInviteLink(UUID actorId, UUID groupId, Long expiresInMinutes) {

        GroupEntity group = requireEditableGroup(actorId, groupId);
        Instant expiresAt = expiresInMinutes == null ? null : Instant.now().plus(Duration.ofMinutes(expiresInMinutes));
        group.rotateInviteLink(UUID.randomUUID().toString(), expiresAt);
        groupRepository.save(group);

        return new GroupInviteLinkResponse(group.getInviteLinkToken(), group.getInviteLinkExpiresAt());
    }

    @Override
    @Transactional
    public void revokeInviteLink(UUID actorId, UUID groupId) {
        log.debug("revokeInviteLink start actorId={} groupId={}", actorId, groupId);

        GroupEntity group = requireEditableGroup(actorId, groupId);
        if (group.getInviteLinkToken() == null) {
            log.info("revokeInviteLink no-op actorId={} groupId={} — no invite link was active", actorId, groupId);
            return;
        }

        group.revokeInviteLink();
        groupRepository.save(group);

        log.info("revokeInviteLink success actorId={} groupId={}", actorId, groupId);
    }


    private GroupEntity requireEditableGroup(UUID actorId, UUID groupId) {
        GroupEntity group = groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requirePermission(actorMembership, GroupPermissionAction.CAN_EDIT_GROUP_INFO);
        return group;
    }

    private void publishMemberJoined(UUID groupId, UUID userId, GroupMemberRole role) {
        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_MEMBER_JOINED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_MEMBER_JOINED_ROUTING_KEY,
                groupId,
                "Group",
                new GroupMemberJoinedMessage(userId, role));
    }
}
