package com.chatapp.core.auth.qrlogin;

import java.util.UUID;

import com.chatapp.core.auth.qrlogin.dto.response.QrLoginDeviceInfoResponse;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginInitResponse;
import com.chatapp.core.auth.qrlogin.dto.response.QrLoginStatusResponse;
import com.chatapp.core.auth.result.AuthResult;

public interface QrLoginService {

    QrLoginInitResponse init(String newDeviceIp, String newDeviceUserAgent);

    /** REST fallback/bootstrap only — see QrLoginStatusResponse javadoc. */
    QrLoginStatusResponse getStatus(String qrToken);

    QrLoginDeviceInfoResponse getDeviceInfo(String qrToken, UUID viewerUserId);

    void confirm(String qrToken, UUID approvingUserId);

    AuthResult claim(String qrToken);
}
