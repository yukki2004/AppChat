package com.chatapp.core.auth.qrlogin.dto.response;

/** {@code status} is one of {@code PENDING}/{@code APPROVED}/{@code EXPIRED} — the last one
 *  isn't a real stored state (see QrLoginSessionService.Status), it's what this endpoint reports
 *  once the Redis key is simply gone. REST fallback/bootstrap only — the primary delivery path
 *  for APPROVED is the WS push via realtime-gateway, not polling this endpoint. */
public record QrLoginStatusResponse(String status) {
}
