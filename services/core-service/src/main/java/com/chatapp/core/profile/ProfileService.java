package com.chatapp.core.profile;

import java.util.UUID;

import com.chatapp.core.base.UserResponse;

public interface ProfileService {

    /** #27 — {@code viewerId} sees 404 ({@code USER_NOT_FOUND}) both when {@code targetUserId}
     *  doesn't exist AND when either side has blocked the other — same error, so a blocked
     *  viewer can't tell "blocked" apart from "never existed". See TODO on the impl re:
     *  privacy_settings (#16), which isn't built yet either. */
    UserResponse getPublicProfile(UUID viewerId, UUID targetUserId);
}
