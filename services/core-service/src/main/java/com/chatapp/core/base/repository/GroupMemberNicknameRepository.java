package com.chatapp.core.base.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.GroupMemberNicknameEntity;
import com.chatapp.core.base.entity.GroupMemberNicknameId;

public interface GroupMemberNicknameRepository extends JpaRepository<GroupMemberNicknameEntity, GroupMemberNicknameId> {

    List<GroupMemberNicknameEntity> findByIdGroupIdAndIdUserIdIn(UUID groupId, List<UUID> userIds);
}
