package com.chatapp.core.group.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.constant.GroupJoinRequestStatus;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupJoinRequestEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.repository.GroupJoinRequestRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.GroupJoinService;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.dto.response.GroupPreviewResponse;
import com.chatapp.core.group.dto.response.JoinGroupResponse;
import com.chatapp.core.group.dto.response.JoinRequestResponse;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupJoinServiceImpl implements GroupJoinService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupJoinRequestRepository groupJoinRequestRepository;
    private final GroupPermissionResolver groupPermissionResolver;
    private final GroupMembershipMutator groupMembershipMutator;

    @Override
    @Transactional(readOnly = true)
    public GroupPreviewResponse preview(String token) {
        log.debug("preview start");

        GroupEntity group = resolveByToken(token);

        log.info("preview success groupId={}", group.getId());
        return GroupPreviewResponse.from(group);
    }

    @Override
    @Transactional
    public JoinGroupResponse joinByToken(UUID userId, String token) {
        log.debug("joinByToken start userId={}", userId);

        GroupEntity group = resolveByToken(token);
        if (groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(group.getId(), userId)) {
            throw new AppException(ErrorCode.GROUP_ALREADY_MEMBER);
        }

        if (!group.isRequireApproval()) {
            groupMembershipMutator.addMemberDirectly(group, userId, GroupMemberRole.MEMBER, null);
            log.info("joinByToken success userId={} groupId={} outcome=JOINED", userId, group.getId());
            return new JoinGroupResponse(group.getId(), GroupJoinOutcome.JOINED);
        }

        groupMembershipMutator.createOrReuseJoinRequest(group.getId(), userId, null);
        log.info("joinByToken success userId={} groupId={} outcome=PENDING_APPROVAL", userId, group.getId());
        return new JoinGroupResponse(group.getId(), GroupJoinOutcome.PENDING_APPROVAL);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listJoinRequests(UUID actorId, UUID groupId) {
        log.debug("listJoinRequests start actorId={} groupId={}", actorId, groupId);

        requireApprovalPermission(actorId, groupId);
        List<JoinRequestResponse> result = groupJoinRequestRepository
                .findByGroupIdAndStatus(groupId, GroupJoinRequestStatus.PENDING)
                .stream()
                .map(JoinRequestResponse::from)
                .toList();

        log.info("listJoinRequests success actorId={} groupId={} count={}", actorId, groupId, result.size());
        return result;
    }

    @Override
    @Transactional
    public void approveJoinRequest(UUID actorId, UUID groupId, UUID requestId) {
        log.debug("approveJoinRequest start actorId={} groupId={} requestId={}", actorId, groupId, requestId);

        requireApprovalPermission(actorId, groupId);
        GroupEntity group = groupRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        GroupJoinRequestEntity request = requirePendingRequest(groupId, requestId);

        request.approve(actorId);
        groupJoinRequestRepository.save(request);
        groupMembershipMutator.addMemberDirectly(group, request.getUserId(), GroupMemberRole.MEMBER, request.getInvitedBy());

        log.info("approveJoinRequest success actorId={} groupId={} requestId={}", actorId, groupId, requestId);
    }

    @Override
    @Transactional
    public void rejectJoinRequest(UUID actorId, UUID groupId, UUID requestId, String reason) {
        log.debug("rejectJoinRequest start actorId={} groupId={} requestId={}", actorId, groupId, requestId);

        requireApprovalPermission(actorId, groupId);
        GroupJoinRequestEntity request = requirePendingRequest(groupId, requestId);

        request.reject(actorId, reason);
        groupJoinRequestRepository.save(request);

        log.info("rejectJoinRequest success actorId={} groupId={} requestId={}", actorId, groupId, requestId);
    }

    private void requireApprovalPermission(UUID actorId, UUID groupId) {
        GroupMemberEntity actorMembership = groupPermissionResolver.requireActiveMember(groupId, actorId);
        groupPermissionResolver.requirePermission(actorMembership, GroupPermissionAction.CAN_APPROVE_MEMBERS);
    }

    private GroupJoinRequestEntity requirePendingRequest(UUID groupId, UUID requestId) {
        GroupJoinRequestEntity request = groupJoinRequestRepository.findByIdAndGroupId(requestId, groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND));
        if (request.getStatus() != GroupJoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND);
        }
        return request;
    }

    private GroupEntity resolveByToken(String token) {
        return groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)
                .or(() -> groupRepository.findByInviteLinkTokenAndIsDeletedFalse(token))
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_INVITE_INVALID));
    }
}
