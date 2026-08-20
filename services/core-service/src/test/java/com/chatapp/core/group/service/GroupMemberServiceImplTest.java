package com.chatapp.core.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupAdminPermissionId;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.GroupAdminPermissionRepository;
import com.chatapp.core.base.repository.GroupMemberNicknameRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.dto.response.GroupMemberListResponse;
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
    private GroupMemberNicknameRepository groupMemberNicknameRepository;
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
                groupRepository, groupMemberRepository, groupAdminPermissionRepository, groupMemberNicknameRepository,
                userRepository, groupPermissionResolver, groupMembershipMutator, outboxEventPublisher);
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

    @Test
    void transferOwnership_swapsRoles_whenActorIsOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.MEMBER);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));

        groupMemberService.transferOwnership(actorId, groupId, targetUserId);

        assertThat(actorMembership.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(targetMembership.getRole()).isEqualTo(GroupMemberRole.OWNER);
        verify(groupMemberRepository).saveAndFlush(actorMembership);
        verify(groupMemberRepository).saveAndFlush(targetMembership);
        verify(groupAdminPermissionRepository, never()).save(any());
        verify(groupAdminPermissionRepository).deleteById(new GroupAdminPermissionId(groupId, targetUserId));
        verify(outboxEventPublisher, org.mockito.Mockito.times(2)).publish(any(), any(), eq(groupId), eq("Group"), any());
    }

    @Test
    void transferOwnership_throwsConcurrentModification_whenUniqueOwnerIndexConflicts() {
        // 2 concurrent transferOwnership calls off the same Owner: the loser's saveAndFlush of the
        // new Owner hits idx_group_members_one_active_owner because the winner already committed
        // first — no advisory lock, the DB unique index is the actual safety net (see migration
        // V20260820100000).
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        GroupMemberEntity targetMembership = activeMember(GroupMemberRole.MEMBER);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.of(targetMembership));
        when(groupMemberRepository.saveAndFlush(argThat(e -> e == actorMembership))).thenReturn(actorMembership);
        when(groupMemberRepository.saveAndFlush(argThat(e -> e == targetMembership)))
                .thenThrow(new DataIntegrityViolationException("idx_group_members_one_active_owner"));

        assertThatThrownBy(() -> groupMemberService.transferOwnership(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_CONCURRENT_MODIFICATION);
        verify(groupMemberRepository).saveAndFlush(actorMembership);
    }

    @Test
    void transferOwnership_throwsSelfTransferNotAllowed_whenTargetIsActor() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);

        assertThatThrownBy(() -> groupMemberService.transferOwnership(actorId, groupId, actorId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SELF_TRANSFER_NOT_ALLOWED);
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void transferOwnership_throwsPermissionDenied_whenActorIsNotOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        doThrow(new AppException(ErrorCode.GROUP_PERMISSION_DENIED))
                .when(groupPermissionResolver).requireOwner(actorMembership);

        assertThatThrownBy(() -> groupMemberService.transferOwnership(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void transferOwnership_throwsTargetNotAMember_whenTargetNotActive() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity actorMembership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(actorMembership);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, targetUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.transferOwnership(actorId, groupId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_TARGET_NOT_A_MEMBER);
    }

    @Test
    void leave_removesMember_whenActorIsPlainMember() {
        GroupEntity group = someGroup();
        group.incrementMemberCount();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity membership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.MEMBER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(membership);

        groupMemberService.leave(actorId, groupId);

        assertThat(membership.isActive()).isFalse();
        assertThat(group.getMemberCount()).isZero();
        verify(groupAdminPermissionRepository, never()).deleteById(any());
        verify(outboxEventPublisher).publish(any(), any(), eq(groupId), eq("Group"), any());
    }

    @Test
    void leave_removesAdmin_andDropsAdminPermissionRow() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity membership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.ADMIN, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(membership);

        groupMemberService.leave(actorId, groupId);

        assertThat(membership.isActive()).isFalse();
        verify(groupAdminPermissionRepository).deleteById(new GroupAdminPermissionId(groupId, actorId));
    }

    @Test
    void leave_throwsOwnerCannotLeave_whenActorIsOwner() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity membership = new GroupMemberEntity(groupId, actorId, GroupMemberRole.OWNER, null);
        when(groupPermissionResolver.requireActiveMember(groupId, actorId)).thenReturn(membership);

        assertThatThrownBy(() -> groupMemberService.leave(actorId, groupId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_CANNOT_LEAVE);
        assertThat(membership.isActive()).isTrue();
        verifyNoInteractions(groupAdminPermissionRepository);
    }

    @Test
    void leave_throwsGroupNotFound_whenGroupMissing() {
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.leave(actorId, groupId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        verify(groupPermissionResolver, never()).requireActiveMember(any(), any());
    }

    private static void setJoinedAt(GroupMemberEntity member, Instant joinedAt) {
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
    }

    private static void setId(GroupMemberEntity member, UUID id) {
        ReflectionTestUtils.setField(member, "id", id);
    }

    @Test
    void listMembers_returnsEverythingInOneCall_whenFewerMembersThanPageSize() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity member = new GroupMemberEntity(groupId, targetUserId, GroupMemberRole.MEMBER, null);
        setJoinedAt(member, Instant.now());
        when(groupMemberRepository.findPage(eq(groupId), isNull(), isNull(), any()))
                .thenReturn(List.of(member));
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(targetUserId);
        when(user.getDisplayName()).thenReturn("Alice");
        when(user.getAvatarUrl()).thenReturn("http://avatar/alice.png");
        when(userRepository.findAllById(List.of(targetUserId))).thenReturn(List.of(user));
        when(groupMemberNicknameRepository.findByIdGroupIdAndIdUserIdIn(groupId, List.of(targetUserId)))
                .thenReturn(List.of());

        GroupMemberListResponse result = groupMemberService.listMembers(actorId, groupId, null);

        assertThat(result.nextCursor()).isNull();
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).userId()).isEqualTo(targetUserId);
        assertThat(result.members().get(0).displayName()).isEqualTo("Alice");
        assertThat(result.members().get(0).role()).isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    void listMembers_usesNickname_whenOneIsSetForGroup() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        GroupMemberEntity member = new GroupMemberEntity(groupId, targetUserId, GroupMemberRole.MEMBER, null);
        setJoinedAt(member, Instant.now());
        when(groupMemberRepository.findPage(eq(groupId), isNull(), isNull(), any()))
                .thenReturn(List.of(member));
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(targetUserId);
        when(user.getDisplayName()).thenReturn("Alice");
        when(userRepository.findAllById(List.of(targetUserId))).thenReturn(List.of(user));
        com.chatapp.core.base.entity.GroupMemberNicknameEntity nicknameEntity =
                new com.chatapp.core.base.entity.GroupMemberNicknameEntity(groupId, targetUserId, "Ally", actorId);
        when(groupMemberNicknameRepository.findByIdGroupIdAndIdUserIdIn(groupId, List.of(targetUserId)))
                .thenReturn(List.of(nicknameEntity));

        GroupMemberListResponse result = groupMemberService.listMembers(actorId, groupId, null);

        assertThat(result.members().get(0).displayName()).isEqualTo("Ally");
    }

    @Test
    void listMembers_returnsNextCursor_whenRepositoryHasMoreThanOnePage() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        // 41 rows returned for a 40-item page (DEFAULT_PAGE_SIZE) — the extra 1 signals
        // hasMore=true, matching listMembers()'s "fetch limit+1" trick.
        List<GroupMemberEntity> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 41; i++) {
            GroupMemberEntity m = new GroupMemberEntity(groupId, UUID.randomUUID(), GroupMemberRole.MEMBER, null);
            setJoinedAt(m, Instant.now().plusSeconds(i));
            setId(m, UUID.randomUUID());
            rows.add(m);
        }
        String cursor = groupMemberServiceEncodeCursor(rows.get(0));
        when(groupMemberRepository.findPage(eq(groupId), any(), any(), any())).thenReturn(rows);
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(groupMemberNicknameRepository.findByIdGroupIdAndIdUserIdIn(eq(groupId), any())).thenReturn(List.of());

        GroupMemberListResponse result = groupMemberService.listMembers(actorId, groupId, cursor);

        assertThat(result.members()).hasSize(40);
        assertThat(result.nextCursor()).isNotNull();
    }

    private static String groupMemberServiceEncodeCursor(GroupMemberEntity member) {
        String raw = member.getJoinedAt().toEpochMilli() + "_" + member.getId();
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void listMembers_throwsInvalidCursor_whenCursorMalformed() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupMemberService.listMembers(actorId, groupId, "not-a-valid-cursor"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_INVALID_CURSOR);
    }

    @Test
    void listMembers_throwsGroupNotFound_whenGroupMissing() {
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.listMembers(actorId, groupId, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        verify(groupPermissionResolver, never()).requireActiveMember(any(), any());
    }

    @Test
    void listMembers_propagatesNotAMember_whenActorNotMember() {
        GroupEntity group = someGroup();
        when(groupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        doThrow(new AppException(ErrorCode.GROUP_NOT_A_MEMBER))
                .when(groupPermissionResolver).requireActiveMember(groupId, actorId);

        assertThatThrownBy(() -> groupMemberService.listMembers(actorId, groupId, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_A_MEMBER);
        verifyNoInteractions(groupMemberRepository);
    }
}
