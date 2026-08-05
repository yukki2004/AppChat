package com.chatapp.core.friend.dto.response;

import java.time.Instant;

import com.chatapp.core.base.UserResponse;

import lombok.Value;

@Value
public class FriendRequestResponse {

    UserResponse user;
    String message;
    Instant createdAt;
}
