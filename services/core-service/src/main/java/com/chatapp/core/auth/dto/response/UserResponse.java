package com.chatapp.core.auth.dto.response;

import java.util.UUID;

import com.chatapp.core.user.UserEntity;

import lombok.Value;

@Value
public class UserResponse {

    UUID id;
    String username;
    String displayName;
    String avatarUrl;

    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }
}
