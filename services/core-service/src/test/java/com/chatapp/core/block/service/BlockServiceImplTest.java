package com.chatapp.core.block.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.entity.UserBlockEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.UserBlockRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BlockServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;

    private BlockServiceImpl blockService;

    private final UUID blockerId = UUID.randomUUID();
    private final UUID blockedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        blockService = new BlockServiceImpl(userRepository, userBlockRepository);
    }

    private UserEntity someUser() {
        return new UserEntity("bob", "bob@example.com", null, "hash", "Bob");
    }

    @Test
    void block_rejectsSelfBlock() {
        assertThatThrownBy(() -> blockService.block(blockerId, blockerId, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_BLOCK_NOT_ALLOWED);
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void block_rejectsWhenBlockedUserMissing() {
        when(userRepository.findByIdAndDeletedAtIsNull(blockedId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.block(blockerId, blockedId, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void block_isNoOp_whenAlreadyBlocked() {
        when(userRepository.findByIdAndDeletedAtIsNull(blockedId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)).thenReturn(true);

        blockService.block(blockerId, blockedId, "spam");

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void block_savesRow() {
        when(userRepository.findByIdAndDeletedAtIsNull(blockedId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)).thenReturn(false);

        blockService.block(blockerId, blockedId, "harassment");

        verify(userBlockRepository).save(any(UserBlockEntity.class));
    }

    @Test
    void unblock_deletesRow_idempotentEvenIfNeverBlocked() {
        blockService.unblock(blockerId, blockedId);

        verify(userBlockRepository).deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Test
    void listBlocked_mapsBlockedIdsToUsers() {
        UserBlockEntity block = new UserBlockEntity(blockerId, blockedId, "spam");
        UserEntity blockedUser = withId(someUser(), blockedId);
        when(userBlockRepository.findByBlockerId(blockerId)).thenReturn(List.of(block));
        when(userRepository.findAllById(any())).thenReturn(List.of(blockedUser));

        List<UserResponse> result = blockService.listBlocked(blockerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(blockedId);
    }

    /** UserEntity's id is DB-generated (no public setter) — reflection is the only way to give
     *  a test double a specific id without standing up a real persistence context. */
    private UserEntity withId(UserEntity user, UUID id) {
        try {
            var field = UserEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
