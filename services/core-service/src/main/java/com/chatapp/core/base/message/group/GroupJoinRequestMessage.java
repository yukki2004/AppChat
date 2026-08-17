package com.chatapp.core.base.message.group;

import java.util.UUID;

public record GroupJoinRequestMessage(UUID requestId, UUID userId, UUID invitedBy) {
}
