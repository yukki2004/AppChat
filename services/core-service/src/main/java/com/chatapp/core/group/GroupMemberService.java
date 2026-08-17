package com.chatapp.core.group;

import java.util.UUID;

import com.chatapp.core.group.dto.response.AddMemberResponse;

public interface GroupMemberService {

    AddMemberResponse addMember(UUID actorId, UUID groupId, UUID targetUserId);

    void kickMember(UUID actorId, UUID groupId, UUID targetUserId);

    void promoteToAdmin(UUID actorId, UUID groupId, UUID targetUserId);

    void demoteToMember(UUID actorId, UUID groupId, UUID targetUserId);
}
