package com.chatapp.core.base.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.constant.FriendshipStatus;
import com.chatapp.core.base.entity.FriendshipEntity;

public interface FriendshipRepository extends JpaRepository<FriendshipEntity, UUID> {

    /** #20 accept — the addressee is always known ahead of time (only they may accept), so this
     *  is a plain equality lookup, not the OR-based {@link #findByUnorderedPair} used where the
     *  caller's role isn't known yet (see {@code FriendServiceImpl.cancelOrReject}). */
    Optional<FriendshipEntity> findByRequesterIdAndAddresseeIdAndStatus(
            UUID requesterId, UUID addresseeId, FriendshipStatus status);

    List<FriendshipEntity> findByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);

    List<FriendshipEntity> findByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);

    List<FriendshipEntity> findByRequesterIdAndStatusOrAddresseeIdAndStatus(
            UUID requesterId, FriendshipStatus status1, UUID addresseeId, FriendshipStatus status2);

    Optional<FriendshipEntity> findByRequesterIdAndAddresseeIdOrAddresseeIdAndRequesterId(
            UUID requesterId1, UUID addresseeId1, UUID addresseeId2, UUID requesterId2);

    default Optional<FriendshipEntity> findByUnorderedPair(UUID a, UUID b) {
        return findByRequesterIdAndAddresseeIdOrAddresseeIdAndRequesterId(a, b, a, b);
    }

    default List<FriendshipEntity> findIncomingPending(UUID userId) {
        return findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    default List<FriendshipEntity> findOutgoingPending(UUID userId) {
        return findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    default List<FriendshipEntity> findAllAcceptedForUser(UUID userId) {
        return findByRequesterIdAndStatusOrAddresseeIdAndStatus(
                userId, FriendshipStatus.ACCEPTED, userId, FriendshipStatus.ACCEPTED);
    }
}
