package com.chatapp.core.base.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.GroupMemberEntity;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    boolean existsByGroupIdAndUserIdAndIsActiveTrue(UUID groupId, UUID userId);

    Optional<GroupMemberEntity> findByGroupIdAndUserIdAndIsActiveTrue(UUID groupId, UUID userId);
}
