package com.chatapp.core.group;

import java.util.UUID;

import com.chatapp.core.group.dto.response.AddMemberResponse;

public interface GroupMemberService {

    AddMemberResponse addMember(UUID actorId, UUID groupId, UUID targetUserId);
}
