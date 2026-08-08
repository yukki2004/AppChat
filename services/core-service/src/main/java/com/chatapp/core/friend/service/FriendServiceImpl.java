package com.chatapp.core.friend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.constant.FriendshipStatus;
import com.chatapp.core.base.entity.CloseFriendEntity;
import com.chatapp.core.base.entity.FriendshipEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.CloseFriendRepository;
import com.chatapp.core.base.repository.FriendshipRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.friend.FriendService;
import com.chatapp.core.friend.dto.response.FriendQrTokenResponse;
import com.chatapp.core.friend.dto.response.FriendRequestResponse;
import com.chatapp.core.friend.dto.response.SendFriendRequestResponse;
import com.chatapp.core.friend.util.FriendRequestResolver;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates #19-22 — guard checks (self-action, user exists) live here; the actual
 * "what does sending a request resolve to" decision is delegated to {@link FriendRequestResolver}
 * — kept out of this class so it doesn't grow unreadable as more friend/block features land.
 *
 * <p>v1 decision (2026-08-09): block is message/call-only (see {@code UserBlockEntity} javadoc)
 * and does NOT affect friend requests — nothing here checks {@code user_blocks}.
 */
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final CloseFriendRepository closeFriendRepository;
    private final FriendRequestResolver friendRequestResolver;
    private final FriendQrTokenService friendQrTokenService;

    @Override
    @Transactional
    public SendFriendRequestResponse sendRequest(UUID requesterId, UUID addresseeId, String message) {
        if (requesterId.equals(addresseeId)) {
            throw new AppException(ErrorCode.SELF_FRIEND_REQUEST_NOT_ALLOWED);
        }

        userRepository.findByIdAndDeletedAtIsNull(requesterId)
                .orElseThrow(() -> new AppException(ErrorCode.REQUESTER_NOT_FOUND));
        userRepository.findByIdAndDeletedAtIsNull(addresseeId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // TODO: publish friend.request_sent (RoutingKeys.UserExchange, user.exchange) once the
        // outbox pattern is wired up for this service — see skills/outbox-pattern.md. Per
        // docs/.../02-rabbitmq-exchange-map.md the documented consumer is Notification Service
        // (push FCM/APNs/in-app to the addressee). Separately — NOT in the current spec, a
        // decision to confirm later — Realtime Gateway could also consume the same event to
        // push a live WS update to the addressee if they're already connected
        // (cache:ws:user:{user_id}), so the incoming-requests screen updates instantly instead
        // of waiting for the user to reopen/refresh it via GET /friends/requests/incoming. The
        // auto-accept branch (FriendRequestResolver returning "ACCEPTED") should publish
        // friend.accepted instead — same TODO reasoning as FriendServiceImpl.accept().

        return friendRequestResolver.resolve(requesterId, addresseeId, message);
    }

    @Override
    public FriendQrTokenResponse createQrToken(UUID userId) {
        FriendQrTokenService.Result result = friendQrTokenService.create(userId);
        return new FriendQrTokenResponse(result.token(), result.expiresAt());
    }

    @Override
    @Transactional
    public SendFriendRequestResponse sendRequestByQrToken(UUID requesterId, String qrToken, String message) {
        UUID addresseeId = friendQrTokenService.resolve(qrToken)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_QR_TOKEN_INVALID));
        friendQrTokenService.recordUse(qrToken);
        return sendRequest(requesterId, addresseeId, message);
    }

    @Override
    public void revokeQrToken(UUID userId) {
        friendQrTokenService.revoke(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listIncomingPending(UUID userId) {
        List<FriendshipEntity> pending = friendshipRepository.findIncomingPending(userId);
        Map<UUID, UserEntity> otherUsers = loadOtherUsers(pending, FriendshipEntity::getRequesterId);
        return pending.stream()
                .map(f -> toResponse(f, otherUsers.get(f.getRequesterId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listOutgoingPending(UUID userId) {
        List<FriendshipEntity> pending = friendshipRepository.findOutgoingPending(userId);
        Map<UUID, UserEntity> otherUsers = loadOtherUsers(pending, FriendshipEntity::getAddresseeId);
        return pending.stream()
                .map(f -> toResponse(f, otherUsers.get(f.getAddresseeId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public void accept(UUID currentUserId, UUID otherUserId) {
        // Only the addressee may accept, so the direction is already known here — no need for
        // the OR-based findByUnorderedPair used where the caller's role isn't known yet (see
        // cancelOrReject below).
        FriendshipEntity friendship = friendshipRepository
                .findByRequesterIdAndAddresseeIdAndStatus(otherUserId, currentUserId, FriendshipStatus.PENDING)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        friendship.accept();
        friendshipRepository.save(friendship);
        // TODO: publish friend.accepted (RabbitConstant.UserExchange) once the outbox pattern is
        // wired up for this service — see skills/outbox-pattern.md.
    }

    @Override
    @Transactional
    public void cancelOrReject(UUID currentUserId, UUID otherUserId) {
        FriendshipEntity friendship = friendshipRepository.findByUnorderedPair(currentUserId, otherUserId)
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (friendship.getRequesterId().equals(currentUserId)) {
            friendship.cancel();
        } else {
            friendship.reject();
        }
        friendshipRepository.save(friendship);
        // No event to publish here per docs/.../02-rabbitmq-exchange-map.md — friend.request_sent
        // covers #19, friend.accepted covers #20, friend.removed covers #22; reject/cancel isn't
        // announced to the other side (avoids "you got rejected" awkwardness, see spec note).
    }

    @Override
    @Transactional
    public void unfriend(UUID currentUserId, UUID otherUserId) {
        FriendshipEntity friendship = friendshipRepository.findByUnorderedPair(currentUserId, otherUserId)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new AppException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        friendshipRepository.delete(friendship);
        closeFriendRepository.deleteById_UserIdAndId_FriendId(currentUserId, otherUserId);
        closeFriendRepository.deleteById_UserIdAndId_FriendId(otherUserId, currentUserId);
        // TODO: publish friend.removed (RabbitConstant.UserExchange) once the outbox pattern is
        // wired up for this service — see skills/outbox-pattern.md.
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listFriends(UUID userId) {
        // TODO: PublicPresenceDTO (gRPC Presence) per docs/.../03-core-service.md #23 is skipped
        // — no Presence Service client exists anywhere in this codebase yet (Realtime Gateway
        // owns presence, not Core Service). Returns plain public user info only.
        Function<FriendshipEntity, UUID> otherIdOf =
                f -> f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
        List<FriendshipEntity> accepted = friendshipRepository.findAllAcceptedForUser(userId);
        Map<UUID, UserEntity> otherUsers = loadOtherUsers(accepted, otherIdOf);
        return accepted.stream()
                .map(f -> otherUsers.get(otherIdOf.apply(f)))
                .filter(Objects::nonNull)
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void addCloseFriend(UUID userId, UUID targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new AppException(ErrorCode.SELF_CLOSE_FRIEND_NOT_ALLOWED);
        }
        friendshipRepository.findByUnorderedPair(userId, targetUserId)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new AppException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        if (closeFriendRepository.existsById_UserIdAndId_FriendId(userId, targetUserId)) {
            return;
        }
        closeFriendRepository.save(new CloseFriendEntity(userId, targetUserId));
    }

    @Override
    @Transactional
    public void removeCloseFriend(UUID userId, UUID targetUserId) {
        closeFriendRepository.deleteById_UserIdAndId_FriendId(userId, targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listCloseFriends(UUID userId) {
        List<CloseFriendEntity> closeFriends = closeFriendRepository.findById_UserId(userId);
        List<UUID> friendIds = closeFriends.stream().map(cf -> cf.getId().getFriendId()).distinct().toList();
        Map<UUID, UserEntity> friends = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return closeFriends.stream()
                .map(cf -> friends.get(cf.getId().getFriendId()))
                .filter(Objects::nonNull)
                .map(UserResponse::from)
                .toList();
    }

    private Map<UUID, UserEntity> loadOtherUsers(
            List<FriendshipEntity> friendships, Function<FriendshipEntity, UUID> otherIdOf) {
        List<UUID> otherIds = friendships.stream().map(otherIdOf).distinct().toList();
        return userRepository.findAllById(otherIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private FriendRequestResponse toResponse(FriendshipEntity friendship, UserEntity otherUser) {
        if (otherUser == null) {
            return null;
        }
        return new FriendRequestResponse(UserResponse.from(otherUser), friendship.getMessage(), friendship.getCreatedAt());
    }
}
