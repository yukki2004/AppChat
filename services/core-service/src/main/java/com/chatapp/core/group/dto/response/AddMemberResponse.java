package com.chatapp.core.group.dto.response;

import java.util.UUID;

public record AddMemberResponse(UUID groupId, UUID userId, GroupJoinOutcome outcome) {
}
