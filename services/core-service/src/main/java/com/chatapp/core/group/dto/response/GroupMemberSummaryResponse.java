package com.chatapp.core.group.dto.response;

import java.util.UUID;

import com.chatapp.core.base.constant.GroupMemberRole;

/** {@code displayName} is already resolved server-side — nickname in this group if one is set,
 *  otherwise the user's profile display name — so callers never juggle 2 name sources. */
public record GroupMemberSummaryResponse(
        UUID userId, String displayName, String avatarUrl, GroupMemberRole role) {
}
