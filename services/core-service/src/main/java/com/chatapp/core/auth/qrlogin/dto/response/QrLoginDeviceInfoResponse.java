package com.chatapp.core.auth.qrlogin.dto.response;

/** Shown to the ALREADY-logged-in device right after it scans the QR, so the user can verify
 *  "is this really me trying to log in on a new device" before confirming — the security gate
 *  that substitutes for 2FA on the new device (see AuthService#issueTokensForDevice javadoc). */
public record QrLoginDeviceInfoResponse(String ipAddress, String userAgent, String loginCountry, String loginCity) {
}
