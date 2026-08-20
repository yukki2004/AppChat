package com.chatapp.core.group.dto.response;

import java.util.List;

public record GroupMemberListResponse(List<GroupMemberSummaryResponse> members, String nextCursor) {
}
