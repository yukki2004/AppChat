package com.chatapp.core.base.message.group;

import java.util.UUID;

import com.chatapp.core.base.constant.GroupMemberRole;

public record GroupMemberJoinedMessage(UUID userId, GroupMemberRole role) {
}
