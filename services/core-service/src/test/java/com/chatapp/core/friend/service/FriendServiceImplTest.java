package com.chatapp.core.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.chatapp.core.base.constant.FriendshipStatus;
import com.chatapp.core.base.entity.FriendshipEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.CloseFriendRepository;
import com.chatapp.core.base.repository.FriendshipRepository;
import com.chatapp.core.base.repository.UserBlockRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.friend.dto.response.SendFriendRequestResponse;
import com.chatapp.core.friend.util.FriendRequestRateLimiter;
import com.chatapp.core.friend.util.FriendRequestResolver;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private CloseFriendRepository closeFriendRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private FriendServiceImpl friendService;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID addresseeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Real (non-mocked) collaborators wired with the same mocks — sendRequest's actual
        // resolution/rate-limit behavior still runs, just via the extracted classes instead of
        // private methods, so the existing behavioral assertions below don't need to change.
        // userBlockRepository is left unstubbed in most tests below — Mockito defaults an
        // unstubbed boolean-returning method to false, which is exactly "not blocked".
        friendService = new FriendServiceImpl(
                userRepository,
                friendshipRepository,
                userBlockRepository,
                closeFriendRepository,
                new FriendRequestRateLimiter(redisTemplate),
                new FriendRequestResolver(friendshipRepository));
    }

    private void stubNoRateLimitOrCooldown() {
        // hasKey() not stubbed — checkCooldown() call is commented out in sendRequest() for now.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(1L);
    }

    private UserEntity someUser() {
        return new UserEntity("bob", "bob@example.com", null, "hash", "Bob");
    }

    private void assertThrowsErrorCode(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void sendRequest_rejectsSelfRequest() {
        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, requesterId, null),
                ErrorCode.SELF_FRIEND_REQUEST_NOT_ALLOWED);
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void sendRequest_rejectsWhenAddresseeMissing() {
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.empty());

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void sendRequest_rejectsWhenRequesterMissing() {
        // Guards a soft-deleted requester still holding an unexpired access_token.
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.empty());

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.REQUESTER_NOT_FOUND);
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(addresseeId);
    }

    @Test
    void sendRequest_rejectsWhenRequesterBlockedAddressee() {
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(requesterId, addresseeId)).thenReturn(true);

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.FRIEND_REQUEST_NOT_ALLOWED);
        verify(friendshipRepository, never()).findByUnorderedPair(any(), any());
    }

    @Test
    void sendRequest_rejectsWhenAddresseeBlockedRequester() {
        // OR-2-chiều: the block doesn't have to be requester -> addressee to count.
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(requesterId, addresseeId)).thenReturn(false);
        when(userBlockRepository.existsByBlockerIdAndBlockedId(addresseeId, requesterId)).thenReturn(true);

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.FRIEND_REQUEST_NOT_ALLOWED);
    }

    @Test
    void sendRequest_insertsFreshPendingRow_whenNoExistingRow() {
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        stubNoRateLimitOrCooldown();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.empty());

        SendFriendRequestResponse result = friendService.sendRequest(requesterId, addresseeId, "hi");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(friendshipRepository).saveAndFlush(any(FriendshipEntity.class));
    }

    @Test
    void sendRequest_autoAccepts_whenReversePendingAlreadyExists() {
        // B already sent a PENDING request to A; A (requesterId) now sends back to B.
        FriendshipEntity reversePending = new FriendshipEntity(addresseeId, requesterId, "hey");
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        stubNoRateLimitOrCooldown();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(reversePending));

        SendFriendRequestResponse result = friendService.sendRequest(requesterId, addresseeId, "hi back");

        assertThat(result.getStatus()).isEqualTo("ACCEPTED");
        assertThat(reversePending.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        verify(friendshipRepository).save(reversePending);
    }

    @Test
    void sendRequest_rejectsDuplicate_whenSameDirectionAlreadyPending() {
        FriendshipEntity samePending = new FriendshipEntity(requesterId, addresseeId, "hi");
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        stubNoRateLimitOrCooldown();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(samePending));

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, "hi again"),
                ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
    }

    @Test
    void sendRequest_rejectsWhenAlreadyFriends() {
        FriendshipEntity accepted = new FriendshipEntity(requesterId, addresseeId, "hi");
        accepted.accept();
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        stubNoRateLimitOrCooldown();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(accepted));

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.ALREADY_FRIENDS);
    }

    @Test
    void sendRequest_reusesRow_whenPreviouslyRejected() {
        FriendshipEntity rejected = new FriendshipEntity(addresseeId, requesterId, "old message");
        rejected.reject();
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        stubNoRateLimitOrCooldown();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(rejected));

        SendFriendRequestResponse result = friendService.sendRequest(requesterId, addresseeId, "new try");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(rejected.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(rejected.getRequesterId()).isEqualTo(requesterId);
        assertThat(rejected.getAddresseeId()).isEqualTo(addresseeId);
        verify(friendshipRepository).save(rejected);
    }

    @Disabled("checkCooldown() call is commented out in sendRequest() for now — see FriendServiceImpl")
    @Test
    void sendRequest_blockedByCooldown_afterRejection() {
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        when(redisTemplate.hasKey(any())).thenReturn(true);

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.FRIEND_REQUEST_COOLDOWN);
        verify(friendshipRepository, never()).findByUnorderedPair(any(), any());
    }

    @Test
    void sendRequest_rejectsWhenDailyRateLimitExceeded() {
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(51L);

        assertThrowsErrorCode(() -> friendService.sendRequest(requesterId, addresseeId, null),
                ErrorCode.FRIEND_REQUEST_RATE_LIMIT_EXCEEDED);
        verify(friendshipRepository, never()).findByUnorderedPair(any(), any());
    }

    @Test
    void sendRequest_resolvesRaceCondition_asAutoAccept() {
        // Both sides passed the pre-check with no existing row, then this insert lost the race
        // to the DB unique index — the winner's row (reverse PENDING) is now visible on refetch.
        FriendshipEntity winner = new FriendshipEntity(addresseeId, requesterId, "raced in first");
        when(userRepository.findByIdAndDeletedAtIsNull(requesterId)).thenReturn(Optional.of(someUser()));
        when(userRepository.findByIdAndDeletedAtIsNull(addresseeId)).thenReturn(Optional.of(someUser()));
        stubNoRateLimitOrCooldown();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(friendshipRepository.saveAndFlush(any(FriendshipEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        SendFriendRequestResponse result = friendService.sendRequest(requesterId, addresseeId, "hi");

        assertThat(result.getStatus()).isEqualTo("ACCEPTED");
        assertThat(winner.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void accept_marksFriendshipAccepted_whenCurrentUserIsAddressee() {
        // addresseeId sent it, requesterId (current user) is accepting.
        FriendshipEntity pending = new FriendshipEntity(addresseeId, requesterId, "hi");
        when(friendshipRepository.findByRequesterIdAndAddresseeIdAndStatus(addresseeId, requesterId, FriendshipStatus.PENDING))
                .thenReturn(Optional.of(pending));

        friendService.accept(requesterId, addresseeId);

        assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        verify(friendshipRepository).save(pending);
    }

    @Test
    void accept_throwsNotFound_whenCurrentUserIsRequesterNotAddressee() {
        // requesterId sent it themselves — they cannot "accept" their own outgoing request.
        // (otherUserId=addresseeId as requester, currentUserId=requesterId as addressee) simply
        // isn't a row that exists, so the direct lookup correctly comes back empty.
        when(friendshipRepository.findByRequesterIdAndAddresseeIdAndStatus(addresseeId, requesterId, FriendshipStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThrowsErrorCode(() -> friendService.accept(requesterId, addresseeId),
                ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void accept_throwsNotFound_whenNoRowExists() {
        when(friendshipRepository.findByRequesterIdAndAddresseeIdAndStatus(addresseeId, requesterId, FriendshipStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThrowsErrorCode(() -> friendService.accept(requesterId, addresseeId),
                ErrorCode.FRIEND_REQUEST_NOT_FOUND);
    }

    @Test
    void listIncomingPending_mapsRequesterAsOtherUser() {
        FriendshipEntity pending = new FriendshipEntity(addresseeId, requesterId, "hi");
        UserEntity other = withId(someUser(), addresseeId);
        when(friendshipRepository.findIncomingPending(requesterId)).thenReturn(List.of(pending));
        when(userRepository.findAllById(any())).thenReturn(List.of(other));

        List<com.chatapp.core.friend.dto.response.FriendRequestResponse> result =
                friendService.listIncomingPending(requesterId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("hi");
    }

    @Test
    void cancelOrReject_cancels_whenCurrentUserIsRequester() {
        FriendshipEntity pending = new FriendshipEntity(requesterId, addresseeId, "hi");
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(pending));

        friendService.cancelOrReject(requesterId, addresseeId);

        assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.CANCELLED);
        verify(friendshipRepository).save(pending);
    }

    @Test
    void cancelOrReject_rejects_whenCurrentUserIsAddressee() {
        FriendshipEntity pending = new FriendshipEntity(requesterId, addresseeId, "hi");
        when(friendshipRepository.findByUnorderedPair(addresseeId, requesterId)).thenReturn(Optional.of(pending));

        friendService.cancelOrReject(addresseeId, requesterId);

        assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        verify(friendshipRepository).save(pending);
        // Cooldown is intentionally not set — checkCooldown() is disabled by product decision.
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void cancelOrReject_throwsNotFound_whenNoPendingRowExists() {
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.empty());

        assertThrowsErrorCode(() -> friendService.cancelOrReject(requesterId, addresseeId),
                ErrorCode.FRIEND_REQUEST_NOT_FOUND);
    }

    @Test
    void cancelOrReject_throwsNotFound_whenRowIsNotPending() {
        FriendshipEntity accepted = new FriendshipEntity(requesterId, addresseeId, "hi");
        accepted.accept();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(accepted));

        assertThrowsErrorCode(() -> friendService.cancelOrReject(requesterId, addresseeId),
                ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void unfriend_deletesAcceptedFriendship() {
        FriendshipEntity accepted = new FriendshipEntity(requesterId, addresseeId, "hi");
        accepted.accept();
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(accepted));

        friendService.unfriend(requesterId, addresseeId);

        verify(friendshipRepository).delete(accepted);
        verify(closeFriendRepository).deleteById_UserIdAndId_FriendId(requesterId, addresseeId);
        verify(closeFriendRepository).deleteById_UserIdAndId_FriendId(addresseeId, requesterId);
    }

    @Test
    void unfriend_throwsNotFound_whenNoRowExists() {
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.empty());

        assertThrowsErrorCode(() -> friendService.unfriend(requesterId, addresseeId),
                ErrorCode.FRIENDSHIP_NOT_FOUND);
    }

    @Test
    void unfriend_throwsNotFound_whenRowIsNotAccepted() {
        FriendshipEntity pending = new FriendshipEntity(requesterId, addresseeId, "hi");
        when(friendshipRepository.findByUnorderedPair(requesterId, addresseeId)).thenReturn(Optional.of(pending));

        assertThrowsErrorCode(() -> friendService.unfriend(requesterId, addresseeId),
                ErrorCode.FRIENDSHIP_NOT_FOUND);
        verify(friendshipRepository, never()).delete(any());
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
