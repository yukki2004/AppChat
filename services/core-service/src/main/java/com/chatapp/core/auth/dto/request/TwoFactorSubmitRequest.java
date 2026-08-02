package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorSubmitRequest {

    @NotBlank
    private String code;
}
