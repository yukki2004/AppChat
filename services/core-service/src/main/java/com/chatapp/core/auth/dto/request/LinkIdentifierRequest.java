package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Adds email XOR phone to an account that registered with only the other one — re-authed with
 *  the current password (same reasoning as TwoFactorSettingsService#disableMethod: a hijacked
 *  session shouldn't be able to attach a new login identifier on its own). */
@Getter
@Setter
public class LinkIdentifierRequest {

    @NotBlank
    private String password;

    @Email
    private String email;

    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone number must be in E.164 format, e.g. +84901234567")
    private String phone;

    @AssertTrue(message = "Provide exactly one of email or phone, not both")
    public boolean isExactlyOneOfEmailOrPhoneProvided() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail ^ hasPhone;
    }
}
