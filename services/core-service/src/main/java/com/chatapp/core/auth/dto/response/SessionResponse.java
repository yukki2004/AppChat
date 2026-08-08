package com.chatapp.core.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.chatapp.core.base.entity.UserSessionEntity;


public record SessionResponse(
        UUID id,
        String deviceId,
        String deviceName,
        String platform,
        String ipAddress,
        String userAgent,
        String loginCountry,
        String loginCity,
        Instant createdAt,
        Instant lastActiveAt,
        Instant expiresAt,
        boolean isCurrent
) {
    public static SessionResponse from(UserSessionEntity session, boolean isCurrent) {
        return new SessionResponse(
                session.getId(),
                session.getDeviceId(),
                session.getDeviceName(),
                session.getPlatform(),
                session.getIpAddress(),
                session.getUserAgent(),
                session.getLoginCountry(),
                session.getLoginCity(),
                session.getCreatedAt(),
                session.getLastActiveAt(),
                session.getExpiresAt(),
                isCurrent
        );
    }
}
