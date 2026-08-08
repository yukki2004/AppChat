package com.chatapp.core.profile.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.profile.ProfileService;

import lombok.RequiredArgsConstructor;

/** v1 decision (2026-08-09): block is message/call-only (see {@code UserBlockEntity} javadoc) —
 *  it does NOT hide the profile, so this does not check {@code user_blocks}. */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(UUID viewerId, UUID targetUserId) {
        var target = userRepository.findByIdAndDeletedAtIsNull(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // TODO: privacy_settings (#16, who_can_see_profile) isn't built yet — every profile is
        // fully public for now. Wire this up once #16 lands, same "not implemented yet" gap
        // already accepted for who_can_add_friend in sendRequest().
        return UserResponse.from(target);
    }
}
