package com.chatapp.core.base.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.entity.UserBlockEntity;

public interface UserBlockRepository extends JpaRepository<UserBlockEntity, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** OR-2-chiều gộp thành 1 round-trip, dùng ở hot path (sendRequest) thay vì 2 lần gọi
     *  {@link #existsByBlockerIdAndBlockedId} riêng — cùng tận dụng {@code idx_user_blocks_blocker_blocked}
     *  cho cả 2 nhánh OR, không quét thêm bảng nào khác. */
    boolean existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(
            UUID blockerId1, UUID blockedId1, UUID blockerId2, UUID blockedId2);

    List<UserBlockEntity> findByBlockerId(UUID blockerId);

    /** Derived delete — generates a plain {@code DELETE ... WHERE} statement, unlike
     *  {@code deleteById()} which fetches first and throws if nothing is found. Unblocking
     *  someone you never blocked must be a no-op, not an error (see {@code BlockServiceImpl}). */
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
