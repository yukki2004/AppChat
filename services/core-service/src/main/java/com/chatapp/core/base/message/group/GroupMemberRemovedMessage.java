package com.chatapp.core.base.message.group;

import java.util.UUID;

public record GroupMemberRemovedMessage(UUID userId, UUID removedBy) {
}
