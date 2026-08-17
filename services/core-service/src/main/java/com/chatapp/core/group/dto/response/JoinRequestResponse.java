package com.chatapp.core.group.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.chatapp.core.base.entity.GroupJoinRequestEntity;

public record JoinRequestResponse(UUID id, UUID groupId, UUID userId, UUID invitedBy, Instant createdAt) {

    public static JoinRequestResponse from(GroupJoinRequestEntity request) {
        return new JoinRequestResponse(
                request.getId(), request.getGroupId(), request.getUserId(), request.getInvitedBy(), request.getCreatedAt());
    }
}
