package com.chatapp.core.auth.oauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthCallbackRequest {

    @NotBlank
    private String code;
}
