package com.chatapp.core.group;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.group.dto.response.GroupInfoResponse;
import com.chatapp.core.group.dto.response.GroupInviteLinkResponse;
import com.chatapp.core.group.dto.response.GroupQrCodeResponse;
import com.chatapp.core.group.dto.response.GroupResponse;

public interface GroupService {

    GroupResponse createGroup(UUID creatorId, String name, String avatarUrl, String description, List<UUID> memberIds);

    GroupInfoResponse updateInfo(UUID actorId, UUID groupId, String name, String description);

    GroupQrCodeResponse generateQrCode(UUID actorId, UUID groupId);

    GroupQrCodeResponse resetQrCode(UUID actorId, UUID groupId);

    GroupInviteLinkResponse generateInviteLink(UUID actorId, UUID groupId);

    GroupInviteLinkResponse resetInviteLink(UUID actorId, UUID groupId);

    void deleteGroup(UUID actorId, UUID groupId);
}
