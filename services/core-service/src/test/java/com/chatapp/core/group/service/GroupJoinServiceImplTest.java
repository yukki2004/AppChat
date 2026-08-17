package com.chatapp.core.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.dto.response.GroupPreviewResponse;
import com.chatapp.core.group.dto.response.JoinGroupResponse;
import com.chatapp.core.group.dto.response.JoinRequestResponse;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

@ExtendWith(MockitoExtension.class)
class GroupJoinServiceImplTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupJoinRequestRepository groupJoinRequestRepository;
    @Mock
    private GroupPermissionResolver groupPermissionResolver;
    @Mock
    private GroupMembershipMutator groupMembershipMutator;

    private GroupJoinServiceImpl groupJoinService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String token = "some-token";

    @BeforeEach
    void setUp() {
        groupJoinService = new GroupJoinServiceImpl(
                groupRepository, groupMemberRepository, groupJoinRequestRepository, groupPermissionResolver,
                groupMembershipMutator);
    }

    private GroupEntity someGroup() {
        return new GroupEntity("Group", null, null, actorId);
    }

    @Test
    void preview_returnsGroupInfo_whenQrTokenMatches() {
        GroupEntity group = someGroup();
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(group));

        GroupPreviewResponse result = groupJoinService.preview(token);

        assertThat(result.name()).isEqualTo("Group");
        assertThat(result.memberCount()).isEqualTo(0);
        verify(groupRepository, never()).findByInviteLinkTokenAndIsDeletedFalse(any());
    }

    @Test
    void preview_returnsGroupInfo_whenOnlyInviteLinkMatches() {
        GroupEntity group = someGroup();
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.empty());
        when(groupRepository.findByInviteLinkTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(group));

        GroupPreviewResponse result = groupJoinService.preview(token);

        assertThat(result.name()).isEqualTo("Group");
    }

    @Test
    void preview_throwsInviteInvalid_whenNoTokenMatches() {
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.empty());
        when(groupRepository.findByInviteLinkTokenAndIsDeletedFalse(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupJoinService.preview(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_INVITE_INVALID);
    }

    @Test
    void joinByToken_addsDirectly_whenApprovalOff() {
        GroupEntity group = someGroup();
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(any(), eq(userId))).thenReturn(false);

        JoinGroupResponse result = groupJoinService.joinByToken(userId, token);

        assertThat(result.outcome()).isEqualTo(GroupJoinOutcome.JOINED);
        verify(groupMembershipMutator).addMemberDirectly(group, userId, GroupMemberRole.MEMBER, null);
        verify(groupMembershipMutator, never()).createOrReuseJoinRequest(any(), any(), any());
    }

    @Test
    void joinByToken_createsPendingRequest_whenApprovalOn() {
        GroupEntity group = someGroup();
        group.setRequireApproval(true);
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(any(), eq(userId))).thenReturn(false);

        JoinGroupResponse result = groupJoinService.joinByToken(userId, token);

        assertThat(result.outcome()).isEqualTo(GroupJoinOutcome.PENDING_APPROVAL);
        verify(groupMembershipMutator).createOrReuseJoinRequest(group.getId(), userId, null);
        verify(groupMembershipMutator, never()).addMemberDirectly(any(), any(), any(), any());
    }

    @Test
    void joinByToken_throwsAlreadyMember_whenAlreadyActive() {
        GroupEntity group = someGroup();
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(any(), eq(userId))).thenReturn(true);

        assertThatThrownBy(() -> groupJoinService.joinByToken(userId, token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ALREADY_MEMBER);
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void joinByToken_throwsInviteInvalid_whenTokenNotFound() {
        when(groupRepository.findByQrCodeTokenAndIsDeletedFalse(token)).thenReturn(Optional.empty());
        when(groupRepository.findByInviteLinkTokenAndIsDeletedFalse(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupJoinService.joinByToken(userId, token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_INVITE_INVALID);
    }

    @Test
    void listJoinRequests_returnsMappedList() {
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupJoinRequestEntity request = new GroupJoinRequestEntity(groupId, userId, null);
        when(groupJoinRequestRepository.findByGroupIdAndStatus(groupId, GroupJoinRequestStatus.PENDING))
                .thenReturn(List.of(request));

        List<JoinRequestResponse> result = groupJoinService.listJoinRequests(actorId, groupId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        verify(groupPermissionResolver).requirePermission(actorMembership, GroupPermissionAction.CAN_APPROVE_MEMBERS);
    }

    @Test
    void listJoinRequests_propagatesPermissionDenied() {
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.MEMBER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requirePermission(actorMembership, GroupPermissionAction.CAN_APPROVE_MEMBERS);

        assertThatThrownBy(() -> groupJoinService.listJoinRequests(actorId, groupId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verifyNoInteractions(groupJoinRequestRepository);
    }

    @Test
    void approveJoinRequest_approvesAndAddsMember() {
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupJoinRequestEntity request = new GroupJoinRequestEntity(groupId, userId, null);
        UUID requestId = UUID.randomUUID();
        when(groupJoinRequestRepository.findByIdAndGroupId(requestId, groupId)).thenReturn(Optional.of(request));

        groupJoinService.approveJoinRequest(actorId, groupId, requestId);

        assertThat(request.getStatus()).isEqualTo(GroupJoinRequestStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(actorId);
        verify(groupJoinRequestRepository).save(request);
        verify(groupMembershipMutator).addMemberDirectly(group, userId, GroupMemberRole.MEMBER, null);
    }

    @Test
    void approveJoinRequest_throwsNotFound_whenRequestMissing() {
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(someGroup()));
        UUID requestId = UUID.randomUUID();
        when(groupJoinRequestRepository.findByIdAndGroupId(requestId, groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupJoinService.approveJoinRequest(actorId, groupId, requestId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND);
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void approveJoinRequest_throwsNotFound_whenRequestAlreadyReviewed() {
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupJoinRequestEntity request = new GroupJoinRequestEntity(groupId, userId, null);
        request.approve(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        when(groupJoinRequestRepository.findByIdAndGroupId(requestId, groupId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> groupJoinService.approveJoinRequest(actorId, groupId, requestId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND);
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void rejectJoinRequest_rejectsRequest() {
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupJoinRequestEntity request = new GroupJoinRequestEntity(groupId, userId, null);
        UUID requestId = UUID.randomUUID();
        when(groupJoinRequestRepository.findByIdAndGroupId(requestId, groupId)).thenReturn(Optional.of(request));

        groupJoinService.rejectJoinRequest(actorId, groupId, requestId, "not a good fit");

        assertThat(request.getStatus()).isEqualTo(GroupJoinRequestStatus.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo("not a good fit");
        verify(groupJoinRequestRepository).save(request);
        verifyNoInteractions(groupMembershipMutator);
    }
}
