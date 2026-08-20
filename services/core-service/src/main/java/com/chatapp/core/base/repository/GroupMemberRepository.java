package com.chatapp.core.base.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatapp.core.base.entity.GroupMemberEntity;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    boolean existsByGroupIdAndUserIdAndIsActiveTrue(UUID groupId, UUID userId);

    Optional<GroupMemberEntity> findByGroupIdAndUserIdAndIsActiveTrue(UUID groupId, UUID userId);

    /** Keyset pagination on (joined_at, id) — {@code afterJoinedAt}/{@code afterId} both NULL
     *  fetches the first page. No OFFSET, so page N+1 is never affected by rows added/removed
     *  before the cursor position. See GroupMemberServiceImpl#listMembers. */
    @Query("SELECT m FROM GroupMemberEntity m WHERE m.groupId = :groupId AND m.isActive = true "
            + "AND (:afterJoinedAt IS NULL "
            + "     OR m.joinedAt > :afterJoinedAt "
            + "     OR (m.joinedAt = :afterJoinedAt AND m.id > :afterId)) "
            + "ORDER BY m.joinedAt ASC, m.id ASC")
    List<GroupMemberEntity> findPage(
            @Param("groupId") UUID groupId,
            @Param("afterJoinedAt") Instant afterJoinedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);
}
