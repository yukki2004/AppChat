package com.chatapp.core.profile.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.repository.UserBlockRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.profile.ProfileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(UUID viewerId, UUID targetUserId) {
        var target = userRepository.findByIdAndDeletedAtIsNull(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Same error as "doesn't exist" on purpose — a blocked viewer must not be able to tell
        // blocked apart from deleted/never-existed. OR-2-chiều: either side blocking hides it,
        // gộp thành 1 query (giống isBlockedEitherDirection ở FriendServiceImpl.sendRequest).
        if (userBlockRepository.existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(
                viewerId, targetUserId, targetUserId, viewerId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        // TODO: privacy_settings (#16, who_can_see_profile) isn't built yet — every profile is
        // fully public to any non-blocked viewer for now. Wire this up once #16 lands, same
        // "not implemented yet" gap already accepted for who_can_add_friend in sendRequest().
        return UserResponse.from(target);
    }
}
