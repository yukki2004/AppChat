package com.chatapp.core.group.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Body is optional (#4 — TTL is optional per spec) — omitted entirely means no expiry. */
@Getter
@Setter
public class GenerateInviteLinkRequest {

    @Positive
    private Long expiresInMinutes;
}
