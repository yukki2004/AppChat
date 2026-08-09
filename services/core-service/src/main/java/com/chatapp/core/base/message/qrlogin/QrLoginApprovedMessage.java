package com.chatapp.core.base.message.qrlogin;

/**
 * Payload for `RabbitConstant.UserExchange.USER_QR_LOGIN_APPROVED_*` — deliberately holds ONLY
 * `qrToken`: realtime-gateway's registry is keyed by qr_token (`cache:ws:qr_login:{qr_token}`),
 * never by user_id, since the new device isn't authenticated yet and has no user_id of its own
 * to look up by (see caller in QrLoginServiceImpl for where the approving user_id actually goes
 * — straight into the outbox_events `aggregate_id` column, not into this payload).
 *
 * On the wire this serializes as {@code {"qr_token": "..."}}, NOT {@code {"qrToken": ...}} —
 * `spring.jackson.property-naming-strategy=SNAKE_CASE` (application.yml) applies globally,
 * including here. realtime-gateway's consumer must tag its matching Go struct field
 * `json:"qr_token"` accordingly (see internal/consumer/qr_login_consumer.go).
 */
public record QrLoginApprovedMessage(String qrToken) {
}
