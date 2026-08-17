package com.chatapp.core.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupEntity;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.base.repository.GroupRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupJoinOutcome;
import com.chatapp.core.group.util.GroupPermissionResolver;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceImplTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupPermissionResolver groupPermissionResolver;
    @Mock
    private GroupMembershipMutator groupMembershipMutator;

    private GroupMemberServiceImpl groupMemberService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        groupMemberService = new GroupMemberServiceImpl(
                groupRepository, groupMemberRepository, userRepository, groupPermissionResolver, groupMembershipMutator);
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
}
