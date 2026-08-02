package com.chatapp.core.twofactor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Password re-entry, not the method's own code — disabling is most often because the user
 *  LOST access to the method being removed (e.g. lost phone with the authenticator app), so
 *  requiring that same method's code would lock them out of disabling it. */
@Getter
@Setter
public class DisableTwoFactorRequest {

    @NotBlank
    private String password;
}
