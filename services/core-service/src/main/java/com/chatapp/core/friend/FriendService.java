package com.chatapp.core.friend;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.friend.dto.response.FriendRequestResponse;
import com.chatapp.core.friend.dto.response.SendFriendRequestResponse;

public interface FriendService {

    SendFriendRequestResponse sendRequest(UUID requesterId, UUID addresseeId, String message);

    List<FriendRequestResponse> listIncomingPending(UUID userId);

    List<FriendRequestResponse> listOutgoingPending(UUID userId);

    void accept(UUID currentUserId, UUID otherUserId);

    /** #21 — {@code currentUserId} may be either side of the PENDING row: the requester
     *  cancelling their own outgoing request, or the addressee rejecting an incoming one. Which
     *  one happens is decided by the row itself, not by 2 separate endpoints. */
    void cancelOrReject(UUID currentUserId, UUID otherUserId);

    /** #22 — deletes the ACCEPTED friendship row outright. {@code friendships} has no
     *  {@code deleted_at}/soft-delete column (see migration + spec doc) — a removed friendship
     *  frees up the unique pair index so either side can send a brand new request later. */
    void unfriend(UUID currentUserId, UUID otherUserId);

    /** #23 — presence (gRPC Presence) is intentionally NOT included, see TODO on
     *  {@code FriendServiceImpl.listFriends}. */
    List<UserResponse> listFriends(UUID userId);

    /** #26 — {@code targetUserId} must already be an ACCEPTED friend; idempotent if already a
     *  close friend. One-directional, like the underlying product feature (marking someone close
     *  doesn't mean they marked you back). */
    void addCloseFriend(UUID userId, UUID targetUserId);

    /** #26 — idempotent if not currently a close friend. */
    void removeCloseFriend(UUID userId, UUID targetUserId);

    List<UserResponse> listCloseFriends(UUID userId);
}
