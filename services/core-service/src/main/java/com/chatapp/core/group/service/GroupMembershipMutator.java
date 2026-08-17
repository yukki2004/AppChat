package com.chatapp.core.group.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.GroupJoinRequestStatus;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.constant.RabbitConstant;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupJoinRequestEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.message.group.GroupJoinRequestMessage;
import com.chatapp.core.base.message.group.GroupMemberJoinedMessage;
import com.chatapp.core.base.repository.GroupJoinRequestRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/** Shared by every path that can put someone into a group ({@code GroupJoinServiceImpl} join-by-
 *  token/approve, {@code GroupMemberServiceImpl} direct add) — 1 place enforces the member-count
 *  cap and publishes the exact same events, so no path can drift from the others. */
@Component
@RequiredArgsConstructor
class GroupMembershipMutator {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupJoinRequestRepository groupJoinRequestRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    void addMemberDirectly(GroupEntity group, UUID userId, GroupMemberRole role, UUID addedBy) {
        if (group.getMemberCount() >= group.getMaxMembers()) {
            throw new AppException(ErrorCode.GROUP_MEMBER_LIMIT_REACHED);
        }

        GroupMemberEntity member = new GroupMemberEntity(group.getId(), userId, role, addedBy);
        groupMemberRepository.save(member);
        group.incrementMemberCount();
        groupRepository.save(group);

        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_MEMBER_JOINED_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_MEMBER_JOINED_ROUTING_KEY,
                group.getId(),
                "Group",
                new GroupMemberJoinedMessage(userId, role));
    }

    /** Re-scanning the same QR/link (or being re-proposed) before a prior request is reviewed
     *  reuses that row instead of piling up duplicate PENDING requests for the same person. */
    void createOrReuseJoinRequest(UUID groupId, UUID userId, UUID invitedBy) {
        Optional<GroupJoinRequestEntity> existing = groupJoinRequestRepository
                .findByGroupIdAndUserIdAndStatus(groupId, userId, GroupJoinRequestStatus.PENDING);
        if (existing.isPresent()) {
            return;
        }

        GroupJoinRequestEntity request = groupJoinRequestRepository.save(new GroupJoinRequestEntity(groupId, userId, invitedBy));
        outboxEventPublisher.publish(
                RabbitConstant.GroupExchange.GROUP_JOIN_REQUEST_EXCHANGE,
                RabbitConstant.GroupExchange.GROUP_JOIN_REQUEST_ROUTING_KEY,
                groupId,
                "Group",
                new GroupJoinRequestMessage(request.getId(), userId, invitedBy));
    }
}
