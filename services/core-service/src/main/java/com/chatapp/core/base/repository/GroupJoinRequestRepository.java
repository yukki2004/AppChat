package com.chatapp.core.base.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.constant.GroupJoinRequestStatus;
import com.chatapp.core.base.entity.GroupJoinRequestEntity;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequestEntity, UUID> {

    Optional<GroupJoinRequestEntity> findByIdAndGroupId(UUID id, UUID groupId);

    Optional<GroupJoinRequestEntity> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, GroupJoinRequestStatus status);

    List<GroupJoinRequestEntity> findByGroupIdAndStatus(UUID groupId, GroupJoinRequestStatus status);
}
