package com.chatapp.core.group.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chatapp.core.base.constant.GroupMemberRole;
import com.chatapp.core.base.entity.GroupAdminPermissionEntity;
import com.chatapp.core.base.entity.GroupAdminPermissionId;
import com.chatapp.core.base.entity.GroupMemberEntity;
import com.chatapp.core.base.repository.GroupAdminPermissionRepository;
import com.chatapp.core.base.repository.GroupMemberRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

/** Role x permission x action truth table for {@link GroupPermissionResolver} — pure logic, no
 *  DB needed beyond the 1 lookup it makes for ADMIN. */
@ExtendWith(MockitoExtension.class)
class GroupPermissionResolverTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupAdminPermissionRepository groupAdminPermissionRepository;

    private GroupPermissionResolver resolver;

    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new GroupPermissionResolver(groupMemberRepository, groupAdminPermissionRepository);
    }

    @ParameterizedTest
    @EnumSource(GroupPermissionAction.class)
    void owner_canPerformEveryAction_withoutConsultingPermissionTable(GroupPermissionAction action) {
        GroupMemberEntity owner = new GroupMemberEntity(groupId, userId, GroupMemberRole.OWNER, null);

        assertThat(resolver.canPerform(owner, action)).isTrue();
        verifyNoInteractions(groupAdminPermissionRepository);
    }

    @ParameterizedTest
    @EnumSource(GroupPermissionAction.class)
    void member_cannotPerformAnyAction_withoutConsultingPermissionTable(GroupPermissionAction action) {
        GroupMemberEntity member = new GroupMemberEntity(groupId, userId, GroupMemberRole.MEMBER, null);

        assertThat(resolver.canPerform(member, action)).isFalse();
        verifyNoInteractions(groupAdminPermissionRepository);
    }

    @Test
    void admin_followsPermissionRow_perAction() {
        GroupMemberEntity admin = new GroupMemberEntity(groupId, userId, GroupMemberRole.ADMIN, null);
        GroupAdminPermissionEntity permission = new GroupAdminPermissionEntity(groupId, userId);
        permission.update(true, false, true, false);
        when(groupAdminPermissionRepository.findById(new GroupAdminPermissionId(groupId, userId)))
                .thenReturn(Optional.of(permission));

        assertThat(resolver.canPerform(admin, GroupPermissionAction.CAN_APPROVE_MEMBERS)).isTrue();
        assertThat(resolver.canPerform(admin, GroupPermissionAction.CAN_KICK_MEMBERS)).isFalse();
        assertThat(resolver.canPerform(admin, GroupPermissionAction.CAN_EDIT_GROUP_INFO)).isTrue();
        assertThat(resolver.canPerform(admin, GroupPermissionAction.CAN_MANAGE_EVENTS)).isFalse();
    }

    @Test
    void admin_failsOpenToFullPermission_whenRowMissing() {
        // Every promotion to ADMIN inserts a full-true row (#11) — a missing row means that
        // insert never ran, not that this Admin should suddenly be locked out.
        GroupMemberEntity admin = new GroupMemberEntity(groupId, userId, GroupMemberRole.ADMIN, null);
        when(groupAdminPermissionRepository.findById(new GroupAdminPermissionId(groupId, userId)))
                .thenReturn(Optional.empty());

        assertThat(resolver.canPerform(admin, GroupPermissionAction.CAN_KICK_MEMBERS)).isTrue();
    }

    @Test
    void requirePermission_throwsPermissionDenied_whenCannotPerform() {
        GroupMemberEntity member = new GroupMemberEntity(groupId, userId, GroupMemberRole.MEMBER, null);

        assertThatThrownBy(() -> resolver.requirePermission(member, GroupPermissionAction.CAN_EDIT_GROUP_INFO))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PERMISSION_DENIED);
    }

    @Test
    void requirePermission_doesNothing_whenAllowed() {
        GroupMemberEntity owner = new GroupMemberEntity(groupId, userId, GroupMemberRole.OWNER, null);

        resolver.requirePermission(owner, GroupPermissionAction.CAN_EDIT_GROUP_INFO);
        // No exception — success.
    }

    @Test
    void requireActiveMember_returnsMembership_whenPresent() {
        GroupMemberEntity member = new GroupMemberEntity(groupId, userId, GroupMemberRole.MEMBER, null);
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, userId)).thenReturn(Optional.of(member));

        assertThat(resolver.requireActiveMember(groupId, userId)).isSameAs(member);
    }

    @Test
    void requireActiveMember_throwsNotAMember_whenAbsent() {
        when(groupMemberRepository.findByGroupIdAndUserIdAndIsActiveTrue(groupId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.requireActiveMember(groupId, userId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_A_MEMBER);
        verify(groupAdminPermissionRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }
}
