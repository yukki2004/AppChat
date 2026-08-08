package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Email only for now — SMS delivery isn't implemented yet anywhere in this service (see
 *  TwoFactorSettingsService), so there's no phone field to accept here either. */
@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    private String email;
}
