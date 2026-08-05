package com.chatapp.core.friend.util;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.constant.FriendshipStatus;
import com.chatapp.core.base.entity.FriendshipEntity;
import com.chatapp.core.base.repository.FriendshipRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.friend.dto.response.SendFriendRequestResponse;

import lombok.RequiredArgsConstructor;

/**
 * Decides + persists what "send a friend request" means for a given pair, given whatever row
 * (if any) already exists for them. This is the trickiest part of #19 — the unordered-pair
 * unique index on {@code friendships} (see {@link FriendshipEntity} javadoc) means there is
 * NEVER more than 1 row per pair, so "sending a request" isn't always a plain insert: it can
 * auto-accept, reuse a rejected/cancelled row, or lose a race to the DB constraint. Kept out of
 * {@code FriendServiceImpl} (which only does guard checks + delegates here) so that file doesn't
 * grow unreadable as more friend/block features land on top of it.
 */
@Component
@RequiredArgsConstructor
public class FriendRequestResolver {

    private final FriendshipRepository friendshipRepository;

    public SendFriendRequestResponse resolve(UUID requesterId, UUID addresseeId, String message) {
        return friendshipRepository.findByUnorderedPair(requesterId, addresseeId)
                .map(existing -> resolveAgainstExisting(existing, requesterId, addresseeId, message))
                .orElseGet(() -> insertNew(requesterId, addresseeId, message));
    }

    /** Existing row for this pair — decide what "sending a request" means given its current
     *  state. See {@link FriendshipEntity} javadoc for why this reuses the row instead of
     *  inserting. */
    private SendFriendRequestResponse resolveAgainstExisting(
            FriendshipEntity existing, UUID requesterId, UUID addresseeId, String message) {
        switch (existing.getStatus()) {
            case ACCEPTED -> throw new AppException(ErrorCode.ALREADY_FRIENDS);
            case PENDING -> {
                if (existing.getRequesterId().equals(requesterId)) {
                    throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
                }
                // Reverse-direction PENDING already exists — sending back auto-accepts.
                existing.accept();
                friendshipRepository.save(existing);
                return new SendFriendRequestResponse("ACCEPTED");
            }
            case REJECTED, CANCELLED -> {
                existing.resendAs(requesterId, addresseeId, message);
                friendshipRepository.save(existing);
                return new SendFriendRequestResponse("PENDING");
            }
            default -> throw new IllegalStateException("Unhandled FriendshipStatus: " + existing.getStatus());
        }
    }

    /** No existing row was found by the pre-check — but 2 people sending to each other at the
     *  exact same instant can both pass that check before either commits (TOCTOU race). The DB
     *  unique index on the unordered pair is the real safety net: whichever insert loses the
     *  race gets {@link DataIntegrityViolationException} instead of a duplicate row, and should
     *  resolve it exactly like the "reverse PENDING already exists" case above, not surface a
     *  500.
     *
     *  MUST use {@code saveAndFlush}, not {@code save} — {@link FriendshipEntity#getId()} is a
     *  Hibernate in-memory-generated UUID (no DB round-trip needed to assign it), so a plain
     *  {@code save()} does not actually run the INSERT until the surrounding
     *  {@code @Transactional} method commits. Without the forced flush here, the unique-index
     *  violation would surface AFTER this method already returned "PENDING" — too late to catch. */
    private SendFriendRequestResponse insertNew(UUID requesterId, UUID addresseeId, String message) {
        try {
            friendshipRepository.saveAndFlush(new FriendshipEntity(requesterId, addresseeId, message));
            return new SendFriendRequestResponse("PENDING");
        } catch (DataIntegrityViolationException raceLost) {
            FriendshipEntity winner = friendshipRepository.findByUnorderedPair(requesterId, addresseeId)
                    .orElseThrow(() -> raceLost);
            if (winner.getStatus() == FriendshipStatus.PENDING && !winner.getRequesterId().equals(requesterId)) {
                winner.accept();
                friendshipRepository.save(winner);
                return new SendFriendRequestResponse("ACCEPTED");
            }
            throw new AppException(ErrorCode.FRIENDSHIP_ALREADY_EXISTS);
        }
    }
}
