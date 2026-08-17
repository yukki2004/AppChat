package com.chatapp.core.group.dto.response;

import java.util.UUID;

public record JoinGroupResponse(UUID groupId, GroupJoinOutcome outcome) {
}
