package com.chatapp.core.friend;

import java.util.List;
import java.util.UUID;

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
}
