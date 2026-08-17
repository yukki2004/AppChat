package com.chatapp.core.group.dto.response;

import java.util.UUID;

import com.chatapp.core.base.entity.GroupEntity;

/** Read-only preview resolved from a QR/invite-link token — shown BEFORE the user commits to
 *  joining, so scanning/opening the link never has a side effect by itself. */
public record GroupPreviewResponse(UUID id, String name, String avatarUrl, int memberCount, boolean requireApproval) {

    public static GroupPreviewResponse from(GroupEntity group) {
        return new GroupPreviewResponse(
                group.getId(), group.getName(), group.getAvatarUrl(), group.getMemberCount(), group.isRequireApproval());
    }
}
