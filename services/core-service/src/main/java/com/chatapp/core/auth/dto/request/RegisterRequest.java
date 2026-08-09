package com.chatapp.core.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Register with email XOR phone — exactly one, never both, never neither. See conversation
 *  decision: registration verifies exactly one channel by OTP; adding the other channel later
 *  is a separate "link identifier" flow with its own verification step, not a second OTP fired
 *  off during registration. See {@link #isExactlyOneOfEmailOrPhoneProvided()}. */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @Email
    private String email;

    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone number must be in E.164 format, e.g. +84901234567")
    private String phone;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Size(max = 100)
    private String displayName;

    @AssertTrue(message = "Provide exactly one of email or phone, not both")
    public boolean isExactlyOneOfEmailOrPhoneProvided() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail ^ hasPhone;
    }
}
