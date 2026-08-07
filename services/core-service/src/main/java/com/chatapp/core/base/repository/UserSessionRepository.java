package com.chatapp.core.base.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.UserSessionEntity;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

    /** "Currently in effect" means both — `isActive` alone isn't enough: nothing ever flips it
     *  to false when a session expires naturally (only an explicit revoke() does), so a session
     *  30+ days past its `expiresAt` would otherwise still read back as active. */
    Optional<UserSessionEntity> findByTokenHashAndIsActiveTrueAndExpiresAtAfter(String tokenHash, Instant now);

    Optional<UserSessionEntity> findByTokenHash(String tokenHash);

    Optional<UserSessionEntity> findByIdAndUserIdAndIsActiveTrueAndExpiresAtAfter(UUID id, UUID userId, Instant now);

    List<UserSessionEntity> findAllByUserIdAndIsActiveTrueAndExpiresAtAfter(UUID userId, Instant now);
}
