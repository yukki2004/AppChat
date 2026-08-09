package com.chatapp.core.auth.qrlogin.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.qrlogin.QrLoginService;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginDeviceInfoResponse;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginInitResponse;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginStatusResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.base.OutboxEventPublisher;
import com.chatapp.core.base.constant.RabbitConstant;
import com.chatapp.core.base.message.qrlogin.QrLoginApprovedMessage;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.geoip.GeoIpService;
import com.chatapp.core.geoip.GeoLookupResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrLoginServiceImpl implements QrLoginService {

    private final QrLoginSessionService qrLoginSessionService;
    private final AuthService authService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final GeoIpService geoIpService;

    @Override
    public QrLoginInitResponse init(String newDeviceIp, String newDeviceUserAgent) {
        log.debug("qrLoginInit start ip={}", newDeviceIp);
        String qrToken = qrLoginSessionService.create(newDeviceIp, newDeviceUserAgent);
        log.info("qrLoginInit success qrToken={}", qrToken);
        return new QrLoginInitResponse(qrToken, QrLoginSessionService.TTL_SECONDS);
    }

    @Override
    public QrLoginStatusResponse getStatus(String qrToken) {
        String status = qrLoginSessionService.getState(qrToken)
                .map(state -> state.status().name())
                .orElse("EXPIRED");
        return new QrLoginStatusResponse(status);
    }

    @Override
    public QrLoginDeviceInfoResponse getDeviceInfo(String qrToken, UUID viewerUserId) {
        log.debug("qrLoginDeviceInfo start qrToken={} viewerUserId={}", qrToken, viewerUserId);
        QrLoginSessionService.State state = requirePendingState(qrToken);
        GeoLookupResult geo = geoIpService.lookup(state.newDeviceIp());
        return new QrLoginDeviceInfoResponse(state.newDeviceIp(), state.newDeviceUserAgent(), geo.country(), geo.city());
    }

    @Override
    @Transactional
    public void confirm(String qrToken, UUID approvingUserId) {
        log.debug("qrLoginConfirm start qrToken={} approvingUserId={}", qrToken, approvingUserId);
        requirePendingState(qrToken);

        qrLoginSessionService.approve(qrToken, approvingUserId);
        outboxEventPublisher.publish(
                RabbitConstant.UserExchange.USER_QR_LOGIN_APPROVED_EXCHANGE,
                RabbitConstant.UserExchange.USER_QR_LOGIN_APPROVED_ROUTING_KEY,
                approvingUserId,
                "User",
                new QrLoginApprovedMessage(qrToken));

        log.info("qrLoginConfirm success qrToken={} approvingUserId={}", qrToken, approvingUserId);
    }

    @Override
    public AuthResult claim(String qrToken) {
        log.debug("qrLoginClaim start qrToken={}", qrToken);
        QrLoginSessionService.State state = qrLoginSessionService.getState(qrToken)
                .orElseThrow(() -> new AppException(ErrorCode.QR_LOGIN_SESSION_NOT_FOUND));
        if (state.status() != QrLoginSessionService.Status.APPROVED) {
            log.warn("qrLoginClaim rejected qrToken={} reason=NOT_APPROVED_YET", qrToken);
            throw new AppException(ErrorCode.QR_LOGIN_NOT_APPROVED_YET);
        }

        AuthResult result = authService.issueTokensForDevice(
                state.approvedByUserId(), state.newDeviceIp(), state.newDeviceUserAgent());
        qrLoginSessionService.delete(qrToken);

        log.info("qrLoginClaim success qrToken={} userId={}", qrToken, state.approvedByUserId());
        return result;
    }

    private QrLoginSessionService.State requirePendingState(String qrToken) {
        QrLoginSessionService.State state = qrLoginSessionService.getState(qrToken)
                .orElseThrow(() -> new AppException(ErrorCode.QR_LOGIN_SESSION_NOT_FOUND));
        if (state.status() != QrLoginSessionService.Status.PENDING) {
            log.warn("qrLoginConfirm/deviceInfo rejected qrToken={} reason=ALREADY_APPROVED", qrToken);
            throw new AppException(ErrorCode.QR_LOGIN_ALREADY_APPROVED);
        }
        return state;
    }
}
