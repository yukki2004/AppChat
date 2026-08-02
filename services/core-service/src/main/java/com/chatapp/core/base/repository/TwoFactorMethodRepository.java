package com.chatapp.core.base.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.TwoFactorMethodEntity;

public interface TwoFactorMethodRepository extends JpaRepository<TwoFactorMethodEntity, UUID> {

    List<TwoFactorMethodEntity> findByUserId(UUID userId);
}
