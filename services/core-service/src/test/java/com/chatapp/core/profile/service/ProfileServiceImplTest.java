package com.chatapp.core.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.UserBlockRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;

    private ProfileServiceImpl profileService;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(userRepository, userBlockRepository);
    }

    private UserEntity someUser() {
        return new UserEntity("bob", "bob@example.com", null, "hash", "Bob");
    }

    @Test
    void getPublicProfile_returnsUser_whenNotBlocked() {
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetUserId)).thenReturn(false);
        when(userBlockRepository.existsByBlockerIdAndBlockedId(targetUserId, viewerId)).thenReturn(false);

        UserResponse result = profileService.getPublicProfile(viewerId, targetUserId);

        assertThat(result.getUsername()).isEqualTo("bob");
    }

    @Test
    void getPublicProfile_throwsNotFound_whenTargetMissing() {
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getPublicProfile(viewerId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getPublicProfile_throwsSameNotFound_whenViewerBlockedTarget() {
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetUserId)).thenReturn(true);

        assertThatThrownBy(() -> profileService.getPublicProfile(viewerId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getPublicProfile_throwsSameNotFound_whenTargetBlockedViewer() {
        when(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetUserId)).thenReturn(false);
        when(userBlockRepository.existsByBlockerIdAndBlockedId(targetUserId, viewerId)).thenReturn(true);

        assertThatThrownBy(() -> profileService.getPublicProfile(viewerId, targetUserId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
