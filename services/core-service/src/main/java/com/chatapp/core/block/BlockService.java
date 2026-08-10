package com.chatapp.core.block;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.base.UserResponse;

public interface BlockService {

    /** #24 — idempotent: blocking someone already blocked is a no-op, not an error. v1 is
     *  message/call-only (see {@code UserBlockEntity} javadoc) — does NOT touch
     *  {@code friendships}/{@code close_friends}, does NOT affect friend requests or profile
     *  visibility; enforcement lives in messaging-service/call-service (not built yet). */
    void block(UUID blockerId, UUID blockedId, String reason);

    /** #25 — idempotent: unblocking someone not currently blocked is a no-op. */
    void unblock(UUID blockerId, UUID blockedId);

    List<UserResponse> listBlocked(UUID blockerId);
}
