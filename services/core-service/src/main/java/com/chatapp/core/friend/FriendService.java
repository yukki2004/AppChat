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
}
