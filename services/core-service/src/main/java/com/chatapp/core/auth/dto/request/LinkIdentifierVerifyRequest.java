package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Completes POST /auth/link/otp — target is the same email/phone that call was made with. */
@Getter
@Setter
public class LinkIdentifierVerifyRequest {

    @NotBlank
    private String target;

    @NotBlank
    private String code;
}
