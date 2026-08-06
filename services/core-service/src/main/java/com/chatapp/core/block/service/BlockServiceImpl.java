package com.chatapp.core.block.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.entity.UserBlockEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.repository.CloseFriendRepository;
import com.chatapp.core.base.repository.FriendshipRepository;
import com.chatapp.core.base.repository.UserBlockRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.block.BlockService;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.lock.PairLockService;

import lombok.RequiredArgsConstructor;

/**
 * {@link #block} acquires {@link PairLockService} FIRST, before any check — otherwise a
 * concurrent {@code FriendServiceImpl.sendRequest()}/{@code accept()} racing on the same pair can
 * interleave and leave a friendship row alive right after a block "cascades" a delete that missed
 * it (write skew: the two transactions touch {@code user_blocks} and {@code friendships}
 * separately, so no unique constraint on either table catches it). See {@link PairLockService}
 * javadoc.
 */
@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final FriendshipRepository friendshipRepository;
    private final CloseFriendRepository closeFriendRepository;
    private final PairLockService pairLockService;

    @Override
    @Transactional
    public void block(UUID blockerId, UUID blockedId, String reason) {
        if (blockerId.equals(blockedId)) {
            throw new AppException(ErrorCode.SELF_BLOCK_NOT_ALLOWED);
        }
        pairLockService.lock(blockerId, blockedId);
        userRepository.findByIdAndDeletedAtIsNull(blockedId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return;
        }
        userBlockRepository.save(new UserBlockEntity(blockerId, blockedId, reason));

        // Block is a full wall — any friendship state between the 2 (PENDING/ACCEPTED/REJECTED/
        // CANCELLED) is wiped, and close-friend status in both directions with it.
        //
        // TODO (not yet built, see UserBlockEntity javadoc): this method only implements "full"
        // block. A narrower "message/call-only" block (no cascade here — stays friends, still
        // visible — only mutes messaging/calls) has been discussed but not scheduled. If it
        // lands, this cascade must run ONLY for the FULL scope, and block()/BlockController need
        // a scope parameter.
        friendshipRepository.findByUnorderedPair(blockerId, blockedId).ifPresent(friendshipRepository::delete);
        closeFriendRepository.deleteById_UserIdAndId_FriendId(blockerId, blockedId);
        closeFriendRepository.deleteById_UserIdAndId_FriendId(blockedId, blockerId);

        // TODO: publish user.blocked (RoutingKeys.UserExchange) once the outbox pattern is wired
        // up for this service — see skills/outbox-pattern.md.
    }

    @Override
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        userBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        // TODO: publish user.unblocked (RoutingKeys.UserExchange) once the outbox pattern is
        // wired up for this service — see skills/outbox-pattern.md.
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listBlocked(UUID blockerId) {
        List<UserBlockEntity> blocks = userBlockRepository.findByBlockerId(blockerId);
        Map<UUID, UserEntity> blockedUsers = userRepository
                .findAllById(blocks.stream().map(UserBlockEntity::getBlockedId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return blocks.stream()
                .map(b -> blockedUsers.get(b.getBlockedId()))
                .filter(Objects::nonNull)
                .map(UserResponse::from)
                .toList();
    }
}
