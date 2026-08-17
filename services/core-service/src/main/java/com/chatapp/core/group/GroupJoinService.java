package com.chatapp.core.group;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.group.dto.response.GroupPreviewResponse;
import com.chatapp.core.group.dto.response.JoinGroupResponse;
import com.chatapp.core.group.dto.response.JoinRequestResponse;

public interface GroupJoinService {

    GroupPreviewResponse preview(String token);

    JoinGroupResponse joinByToken(UUID userId, String token);

    List<JoinRequestResponse> listJoinRequests(UUID actorId, UUID groupId);

    void approveJoinRequest(UUID actorId, UUID groupId, UUID requestId);

    void rejectJoinRequest(UUID actorId, UUID groupId, UUID requestId, String reason);
}
