package com.chatapp.core.base.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.CloseFriendEntity;
import com.chatapp.core.base.entity.CloseFriendId;

public interface CloseFriendRepository extends JpaRepository<CloseFriendEntity, CloseFriendId> {

    List<CloseFriendEntity> findById_UserId(UUID userId);

    boolean existsById_UserIdAndId_FriendId(UUID userId, UUID friendId);

    /** Derived delete — see {@link UserBlockRepository#deleteByBlockerIdAndBlockedId}, same
     *  reasoning: must be a no-op when the row doesn't exist (unfriend/block cascade calls this
     *  unconditionally for both directions, not knowing in advance which ones exist). */
    void deleteById_UserIdAndId_FriendId(UUID userId, UUID friendId);
}
