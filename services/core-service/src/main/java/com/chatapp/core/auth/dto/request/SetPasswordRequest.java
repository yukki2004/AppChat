package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** First-time password for an account that doesn't have one yet (OAuth-only accounts, see
 *  AuthService#setPassword) — no {@code oldPassword} field, unlike ChangePasswordRequest,
 *  because there isn't one to re-enter. */
@Getter
@Setter
public class SetPasswordRequest {

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
