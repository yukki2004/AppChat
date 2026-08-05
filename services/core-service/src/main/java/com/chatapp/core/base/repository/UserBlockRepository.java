package com.chatapp.core.base.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.UserBlockEntity;

public interface UserBlockRepository extends JpaRepository<UserBlockEntity, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlockEntity> findByBlockerId(UUID blockerId);

    /** Derived delete — generates a plain {@code DELETE ... WHERE} statement, unlike
     *  {@code deleteById()} which fetches first and throws if nothing is found. Unblocking
     *  someone you never blocked must be a no-op, not an error (see {@code BlockServiceImpl}). */
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
