package com.chatapp.core.group;

import java.util.UUID;

import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupMemberListResponse;

public interface GroupMemberService {

    AddMemberResponse addMember(UUID actorId, UUID groupId, UUID targetUserId);

    GroupMemberListResponse listMembers(UUID actorId, UUID groupId, String cursor);

    void kickMember(UUID actorId, UUID groupId, UUID targetUserId);

    void promoteToAdmin(UUID actorId, UUID groupId, UUID targetUserId);

    void demoteToMember(UUID actorId, UUID groupId, UUID targetUserId);

    void transferOwnership(UUID actorId, UUID groupId, UUID newOwnerUserId);

    void leave(UUID actorId, UUID groupId);
}
