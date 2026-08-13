package com.chatapp.core.base.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.GroupAdminPermissionEntity;
import com.chatapp.core.base.entity.GroupAdminPermissionId;

public interface GroupAdminPermissionRepository extends JpaRepository<GroupAdminPermissionEntity, GroupAdminPermissionId> {
}
