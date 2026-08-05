package com.chatapp.core.friend.dto.response;

import lombok.Value;

/** {@code status} is {@code "PENDING"} for a normal send, or {@code "ACCEPTED"} when the other
 *  person already had a pending request to us — sending back auto-accepts instead of creating
 *  a second row (see FriendshipEntity unordered-pair invariant). */
@Value
public class SendFriendRequestResponse {

    String status;
}
