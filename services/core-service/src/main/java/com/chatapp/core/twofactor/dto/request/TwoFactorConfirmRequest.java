package com.chatapp.core.twofactor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorConfirmRequest {

    @NotBlank
    private String code;
}
