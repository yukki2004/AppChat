package com.chatapp.core.group.dto.response;

import java.time.Instant;

public record GroupInviteLinkResponse(String inviteLinkToken, Instant inviteLinkExpiresAt) {
}
