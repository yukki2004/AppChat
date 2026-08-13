package com.chatapp.core.group.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.chatapp.core.base.entity.GroupEntity;

public record GroupResponse(
        UUID id,
        String name,
        String avatarUrl,
        String description,
        boolean requireApproval,
        int maxMembers,
        int memberCount,
        UUID createdBy,
        Instant createdAt,
        String qrCodeToken,
        String inviteLinkToken,
        Instant inviteLinkExpiresAt) {

    public static GroupResponse from(GroupEntity group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getAvatarUrl(),
                group.getDescription(),
                group.isRequireApproval(),
                group.getMaxMembers(),
                group.getMemberCount(),
                group.getCreatedBy(),
                group.getCreatedAt(),
                group.getQrCodeToken(),
                group.getInviteLinkToken(),
                group.getInviteLinkExpiresAt());
    }
}
