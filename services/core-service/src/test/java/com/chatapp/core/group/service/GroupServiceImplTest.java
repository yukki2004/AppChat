package com.chatapp.core.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.dto.response.GroupInfoResponse;
import com.chatapp.core.group.dto.response.GroupInviteLinkResponse;
import com.chatapp.core.group.dto.response.GroupQrCodeResponse;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

/** Covers #2 (updateInfo) and #3-5 (QR code / invite link / reset) — #1 (createGroup) already
 *  works and isn't touched by this change, so it's left untested here. */
@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private GroupPermissionResolver groupPermissionResolver;

    private GroupServiceImpl groupService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(
                userRepository, groupRepository, groupMemberRepository, outboxEventPublisher,
                groupPermissionResolver);
    }

    private GroupEntity someGroup(String name, String description) {
        return new GroupEntity(name, null, description, actorId);
    }

    private GroupMemberEntity ownerMembership() {
        return new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
    }

    /** Common happy-path stubbing: group exists, actor is an editable member. Individual tests
     *  override {@code groupRepository}/permission stubs where they need a different outcome. */
    private void stubEditableGroup(GroupEntity group) {
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity membership = ownerMembership();
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(membership);
    }

    @Test
    void updateInfo_throwsGroupNotFound_whenGroupMissingOrDeleted() {
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.updateInfo(actorId, groupId, "New name", null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        verify(groupPermissionResolver, never()).requireActiveMember(any(), any());
    }

    @Test
    void updateInfo_propagatesNotAMember_beforeTouchingPermissionCheck() {
        GroupEntity group = someGroup("Old name", "old desc");
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        when(groupPermissionResolver.requireActiveMember(groupId, actorId))
                .thenThrow(new AppException(ErrorCode.GROUP_NOT_A_MEMBER));

        assertThatThrownBy(() -> groupService.updateInfo(actorId, groupId, "New name", null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_A_MEMBER);
        verify(groupPermissionResolver, never())
                .requirePermission(any(), any());
    }

    @Test
    void updateInfo_throwsPermissionDenied_whenActorCannotEditGroupInfo() {
        GroupEntity group = someGroup("Old name", "old desc");
        stubEditableGroup(group);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requirePermission(any(), eq(GroupPermissionAction.CAN_EDIT_GROUP_INFO));

        assertThatThrownBy(() -> groupService.updateInfo(actorId, groupId, "New name", null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateInfo_renamesGroup() {
        GroupEntity group = someGroup("Old name", "old desc");
        stubEditableGroup(group);

        GroupInfoResponse result = groupService.updateInfo(actorId, groupId, "New name", null);

        assertThat(result.name()).isEqualTo("New name");
        assertThat(result.description()).isEqualTo("old desc");
        verify(groupRepository).save(group);
    }

    @Test
    void updateInfo_changesDescriptionOnly_whenNameNull() {
        GroupEntity group = someGroup("Same name", "old desc");
        stubEditableGroup(group);

        GroupInfoResponse result = groupService.updateInfo(actorId, groupId, null, "new desc");

        assertThat(result.name()).isEqualTo("Same name");
        assertThat(result.description()).isEqualTo("new desc");
    }

    @Test
    void generateQrCode_rotatesToken() {
        GroupEntity group = someGroup("Group", null);
        stubEditableGroup(group);
        String previousToken = group.getQrCodeToken();

        GroupQrCodeResponse result = groupService.generateQrCode(actorId, groupId);

        assertThat(result.qrCodeToken()).isNotNull().isNotEqualTo(previousToken);
        verify(groupRepository).save(group);
    }

    @Test
    void generateQrCode_throwsPermissionDenied_whenActorCannotEditGroupInfo() {
        GroupEntity group = someGroup("Group", null);
        stubEditableGroup(group);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requirePermission(any(), eq(GroupPermissionAction.CAN_EDIT_GROUP_INFO));

        assertThatThrownBy(() -> groupService.generateQrCode(actorId, groupId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void revokeQrCode_clearsToken_whenPresent() {
        GroupEntity group = someGroup("Group", null);
        group.rotateQrCode("some-qr-token");
        stubEditableGroup(group);

        groupService.revokeQrCode(actorId, groupId);

        assertThat(group.getQrCodeToken()).isNull();
        verify(groupRepository).save(group);
    }

    @Test
    void revokeQrCode_isNoOp_whenNoQrCodeActive() {
        GroupEntity group = someGroup("Group", null);
        stubEditableGroup(group);

        groupService.revokeQrCode(actorId, groupId);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void revokeQrCode_throwsPermissionDenied_beforeMutating() {
        GroupEntity group = someGroup("Group", null);
        group.rotateQrCode("some-qr-token");
        stubEditableGroup(group);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requirePermission(any(), eq(GroupPermissionAction.CAN_EDIT_GROUP_INFO));

        assertThatThrownBy(() -> groupService.revokeQrCode(actorId, groupId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        assertThat(group.getQrCodeToken()).isEqualTo("some-qr-token");
    }

    @Test
    void generateInviteLink_rotatesToken() {
        GroupEntity group = someGroup("Group", null);
        stubEditableGroup(group);

        GroupInviteLinkResponse result = groupService.generateInviteLink(actorId, groupId);

        assertThat(result.inviteLinkToken()).isNotNull();
        verify(groupRepository).save(group);
    }

    @Test
    void generateInviteLink_overwritesExistingToken_immediately() {
        GroupEntity group = someGroup("Group", null);
        group.rotateInviteLink("old-token");
        stubEditableGroup(group);

        GroupInviteLinkResponse result = groupService.generateInviteLink(actorId, groupId);

        assertThat(result.inviteLinkToken()).isNotEqualTo("old-token");
    }

    @Test
    void revokeInviteLink_clearsToken_whenPresent() {
        GroupEntity group = someGroup("Group", null);
        group.rotateInviteLink("some-token");
        stubEditableGroup(group);

        groupService.revokeInviteLink(actorId, groupId);

        assertThat(group.getInviteLinkToken()).isNull();
        verify(groupRepository).save(group);
    }

    @Test
    void revokeInviteLink_isNoOp_whenNoLinkActive() {
        GroupEntity group = someGroup("Group", null);
        stubEditableGroup(group);

        groupService.revokeInviteLink(actorId, groupId);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void revokeInviteLink_throwsPermissionDenied_beforeMutating() {
        GroupEntity group = someGroup("Group", null);
        group.rotateInviteLink("some-token");
        stubEditableGroup(group);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requirePermission(any(), eq(GroupPermissionAction.CAN_EDIT_GROUP_INFO));

        assertThatThrownBy(() -> groupService.revokeInviteLink(actorId, groupId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        assertThat(group.getInviteLinkToken()).isEqualTo("some-token");
    }
}
