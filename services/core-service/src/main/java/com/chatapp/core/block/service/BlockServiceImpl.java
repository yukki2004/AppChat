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
import com.chatapp.core.base.repository.UserBlockRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.block.BlockService;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * v1 decision (2026-08-09): block only mutes messaging/calls (enforced later by
 * messaging-service/call-service, not here) — it does NOT cascade into
 * {@code friendships}/{@code close_friends}, does NOT block friend requests, does NOT hide the
 * profile. See {@code UserBlockEntity} javadoc and {@code docs/.../03-core-service.md} #24.
 */
@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    @Override
    @Transactional
    public void block(UUID blockerId, UUID blockedId, String reason) {
        if (blockerId.equals(blockedId)) {
            throw new AppException(ErrorCode.SELF_BLOCK_NOT_ALLOWED);
        }
        userRepository.findByIdAndDeletedAtIsNull(blockedId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return;
        }
        userBlockRepository.save(new UserBlockEntity(blockerId, blockedId, reason));

        // TODO: publish user.blocked (RoutingKeys.UserExchange) once the outbox pattern is wired
        // up for this service — see skills/outbox-pattern.md. Consumers: messaging-service/
        // call-service, to mute messaging/calls for this pair (not built yet).
    }

    @Override
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        userBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        // TODO: publish user.unblocked (RabbitConstant.UserExchange) once the outbox pattern is
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
