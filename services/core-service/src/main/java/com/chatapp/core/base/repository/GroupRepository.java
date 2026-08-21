package com.chatapp.core.base.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatapp.core.base.entity.GroupEntity;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

    Optional<GroupEntity> findByIdAndIsDeletedFalse(UUID id);

    Optional<GroupEntity> findByQrCodeTokenAndIsDeletedFalse(String qrCodeToken);

    Optional<GroupEntity> findByInviteLinkTokenAndIsDeletedFalse(String inviteLinkToken);

    @Modifying
    @Query("UPDATE GroupEntity g SET g.memberCount = g.memberCount + 1 " +
            "WHERE g.id = :groupId AND g.memberCount < g.maxMembers")
    int incrementMemberCountIfUnderLimit(@Param("groupId") UUID groupId);
}
