package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    /** Username, email, or phone number — looked up in that order in AuthServiceImpl. */
    @NotBlank
    private String identifier;

    @NotBlank
    private String password;
}
