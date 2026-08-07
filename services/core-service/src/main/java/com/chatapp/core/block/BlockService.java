package com.chatapp.core.block;

import java.util.List;
import java.util.UUID;

import com.chatapp.core.base.UserResponse;

public interface BlockService {

    /** #24 — idempotent: blocking someone already blocked is a no-op, not an error. Cascades:
     *  drops the {@code friendships} row between the 2 users (any status) and both directions of
     *  {@code close_friends}. */
    void block(UUID blockerId, UUID blockedId, String reason);

    /** #25 — idempotent: unblocking someone not currently blocked is a no-op. */
    void unblock(UUID blockerId, UUID blockedId);

    List<UserResponse> listBlocked(UUID blockerId);
}
