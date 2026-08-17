package com.chatapp.core.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.chatapp.core.base.entity.GroupAdminPermissionId;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.GroupAdminPermissionRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.util.GroupPermissionAction;
import com.chatapp.core.group.util.GroupPermissionResolver;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceImplTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupAdminPermissionRepository groupAdminPermissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupPermissionResolver groupPermissionResolver;
    @Mock
    private GroupMembershipMutator groupMembershipMutator;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private GroupMemberServiceImpl groupMemberService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groupMemberService = new GroupMemberServiceImpl(
                groupRepository, groupMemberRepository, groupAdminPermissionRepository, userRepository,
                groupPermissionResolver, groupMembershipMutator, outboxEventPublisher);
    }

    private GroupMemberEntity activeMember(GroupMemberRole role) {
        return new GroupMemberEntity(groupId, targetUserId, role, null);
    }

    private GroupEntity someGroup() {
        return new GroupEntity("Group", null, null, actorId);
    }

    private void stubGroupAndTarget(GroupEntity group, GroupMemberRole actorRole) {
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, actorRole, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.of(mock(UserEntity.class)));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId)).thenReturn(false);
    }

    private static UserEntity mock(Class<UserEntity> type) {
        return org.mockito.Mockito.mock(type);
    }

    @Test
    void addMember_addsDirectly_whenApprovalOffAndActorIsMember() {
        GroupEntity group = someGroup();
        stubGroupAndTarget(group, GroupMemberRole.MEMBER);

        AddMemberResponse result = groupMemberService.addMember(actorId, groupId, targetUserId);

        assertThat(result.outcome()).isEqualTo(GroupJoinOutcome.JOINED);
        verify(groupMembershipMutator).addMemberDirectly(group, targetUserId, GroupMemberRole.MEMBER, actorId);
        verify(groupMembershipMutator, never()).createOrReuseJoinRequest(any(), any(), any());
    }

    @Test
    void addMember_addsDirectly_whenApprovalOnButActorIsAdmin() {
        GroupEntity group = someGroup();
        group.setRequireApproval(true);
        stubGroupAndTarget(group, GroupMemberRole.ADMIN);

        AddMemberResponse result = groupMemberService.addMember(actorId, groupId, targetUserId);

        assertThat(result.outcome()).isEqualTo(GroupJoinOutcome.JOINED);
        verify(groupMembershipMutator).addMemberDirectly(group, targetUserId, GroupMemberRole.MEMBER, actorId);
    }

    @Test
    void addMember_addsDirectly_whenApprovalOnAndActorIsOwner() {
        GroupEntity group = someGroup();
        group.setRequireApproval(true);
        stubGroupAndTarget(group, GroupMemberRole.OWNER);

        AddMemberResponse result = groupMemberService.addMember(actorId, groupId, targetUserId);

        assertThat(result.outcome()).isEqualTo(GroupJoinOutcome.JOINED);
    }

    @Test
    void addMember_createsPendingRequest_whenApprovalOnAndActorIsMember() {
        GroupEntity group = someGroup();
        group.setRequireApproval(true);
        stubGroupAndTarget(group, GroupMemberRole.MEMBER);

        AddMemberResponse result = groupMemberService.addMember(actorId, groupId, targetUserId);

        assertThat(result.outcome()).isEqualTo(GroupJoinOutcome.PENDING_APPROVAL);
        verify(groupMembershipMutator).createOrReuseJoinRequest(groupId, targetUserId, actorId);
        verify(groupMembershipMutator, never()).addMemberDirectly(any(), any(), any(), any());
    }

    @Test
    void addMember_throwsAlreadyMember_whenTargetAlreadyActive() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.MEMBER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.of(mock(UserEntity.class)));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId)).thenReturn(true);

        assertThatThrownBy(() -> groupMemberService.addMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ALREADY_MEMBER);
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void addMember_throwsUserNotFound_whenTargetMissing() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.MEMBER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.addMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void addMember_throwsGroupNotFound_whenGroupMissing() {
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.addMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        verify(groupPermissionResolver, never()).requireActiveMember(any(), any());
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void addMember_propagatesNotAMember_whenActorNotMember() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        when(groupPermissionResolver.requireActiveMember(groupId, actorId))
                .thenThrow(new AppException(ErrorCode.GROUP_NOT_A_MEMBER));

        assertThatThrownBy(() -> groupMemberService.addMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_A_MEMBER);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(groupMembershipMutator);
    }

    @Test
    void kickMember_removesTarget_whenActorIsOwnerAndTargetIsAdmin() {
        GroupEntity group = someGroup();
        group.incrementMemberCount();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.ADMIN);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        groupMemberService.kickMember(actorId, groupId, targetUserId);

        assertThat(targetMembership.isActive()).isFalse();
        assertThat(group.getMemberCount()).isZero();
        verify(groupAdminPermissionRepository).deleteById(new GroupAdminPermissionId(groupId, targetUserId));
        verify(outboxEventPublisher).publish(any(), any(), eq(groupId), eq("Group"), any());
    }

    @Test
    void kickMember_removesTarget_whenTargetIsMember_doesNotTouchAdminPermissions() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.MEMBER);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        groupMemberService.kickMember(actorId, groupId, targetUserId);

        assertThat(targetMembership.isActive()).isFalse();
        verify(groupAdminPermissionRepository, never()).deleteById(any());
    }

    @Test
    void kickMember_throwsCannotKick_whenTargetIsOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.OWNER);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> groupMemberService.kickMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_CANNOT_KICK_OWNER_OR_ADMIN);
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void kickMember_throwsCannotKick_whenTargetIsAdminAndActorIsNotOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.ADMIN);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> groupMemberService.kickMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_CANNOT_KICK_OWNER_OR_ADMIN);
    }

    @Test
    void kickMember_throwsTargetNotAMember_whenTargetNotActive() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.kickMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_TARGET_NOT_A_MEMBER);
    }

    @Test
    void kickMember_propagatesPermissionDenied_whenActorLacksCanKickMembers() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.MEMBER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requirePermission(actorMembership, GroupPermissionAction.CAN_KICK_MEMBERS);

        assertThatThrownBy(() -> groupMemberService.kickMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void promoteToAdmin_promotesMember_whenActorIsOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.MEMBER);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        groupMemberService.promoteToAdmin(actorId, groupId, targetUserId);

        assertThat(targetMembership.getRole()).isEqualTo(GroupMemberRole.ADMIN);
        verify(groupAdminPermissionRepository).save(argThat(saved -> saved.getId().equals(new GroupAdminPermissionId(groupId, targetUserId))));
        verify(outboxEventPublisher).publish(any(), any(), eq(groupId), eq("Group"), any());
    }

    @Test
    void promoteToAdmin_throwsPermissionDenied_whenActorIsNotOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requireOwner(actorMembership);

        assertThatThrownBy(() -> groupMemberService.promoteToAdmin(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void promoteToAdmin_throwsAlreadyAdmin_whenTargetIsAlreadyAdmin() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.ADMIN);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> groupMemberService.promoteToAdmin(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_TARGET_ALREADY_ADMIN);
        verify(groupAdminPermissionRepository, never()).save(any());
    }

    @Test
    void demoteToMember_demotesAdmin_whenActorIsOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.ADMIN);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        groupMemberService.demoteToMember(actorId, groupId, targetUserId);

        assertThat(targetMembership.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        verify(groupAdminPermissionRepository).deleteById(new GroupAdminPermissionId(groupId, targetUserId));
        verify(outboxEventPublisher).publish(any(), any(), eq(groupId), eq("Group"), any());
    }

    @Test
    void demoteToMember_throwsNotAnAdmin_whenTargetIsPlainMember() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.MEMBER);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> groupMemberService.demoteToMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_TARGET_NOT_AN_ADMIN);
        verify(groupAdminPermissionRepository, never()).deleteById(any());
    }

    @Test
    void demoteToMember_throwsPermissionDenied_whenActorIsNotOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requireOwner(actorMembership);

        assertThatThrownBy(() -> groupMemberService.demoteToMember(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verifyNoInteractions(groupMemberRepository);
    }
}
