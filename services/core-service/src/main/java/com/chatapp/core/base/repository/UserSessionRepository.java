package com.chatapp.core.base.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.UserSessionEntity;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

    Optional<UserSessionEntity> findByTokenHashAndIsActiveTrue(String tokenHash);
}
