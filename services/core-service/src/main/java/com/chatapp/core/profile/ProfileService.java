package com.chatapp.core.profile;

import java.util.UUID;

import com.chatapp.core.base.UserResponse;

public interface ProfileService {

    /** #27 — {@code viewerId} sees 404 ({@code USER_NOT_FOUND}) if {@code targetUserId} doesn't
     *  exist. Block does NOT hide the profile (v1 is message/call-only, see
     *  {@code UserBlockEntity} javadoc). See TODO on the impl re: privacy_settings (#16), which
     *  isn't built yet either. */
    UserResponse getPublicProfile(UUID viewerId, UUID targetUserId);
}
