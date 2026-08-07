package com.chatapp.core.friend.dto.response;

import java.time.Instant;

public record FriendQrTokenResponse(String token, Instant expiresAt) {
}
