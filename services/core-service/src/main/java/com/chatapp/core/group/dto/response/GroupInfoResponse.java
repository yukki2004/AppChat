package com.chatapp.core.group.dto.response;

import java.util.UUID;

import com.chatapp.core.base.entity.GroupEntity;

public record GroupInfoResponse(UUID id, String name, String description) {

    public static GroupInfoResponse from(GroupEntity group) {
        return new GroupInfoResponse(group.getId(), group.getName(), group.getDescription());
    }
}
