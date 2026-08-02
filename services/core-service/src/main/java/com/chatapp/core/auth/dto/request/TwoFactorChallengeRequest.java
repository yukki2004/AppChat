package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorChallengeRequest {

    /** One of the values previously returned in `available_methods`. */
    @NotBlank
    private String method;
}
