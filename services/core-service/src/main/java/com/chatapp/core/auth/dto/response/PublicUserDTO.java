package com.chatapp.core.auth.dto.response;

import java.util.UUID;

import com.chatapp.core.user.UserEntity;

import lombok.Value;

@Value
public class PublicUserDTO {

    UUID id;
    String username;
    String displayName;
    String avatarUrl;

    public static PublicUserDTO from(UserEntity user) {
        return new PublicUserDTO(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }
}
