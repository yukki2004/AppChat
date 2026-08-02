package com.chatapp.core.base.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.LoginAuditLogEntity;

public interface LoginAuditLogRepository extends JpaRepository<LoginAuditLogEntity, UUID> {
}
