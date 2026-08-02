package com.chatapp.core.twofactor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Password re-entry to re-auth before invalidating every existing backup code — same
 *  sensitivity as {@link DisableTwoFactorRequest}: a hijacked session could otherwise mint
 *  itself a fresh fallback and keep long-term access even after the owner reacts. */
@Getter
@Setter
public class RegenerateBackupCodesRequest {

    @NotBlank
    private String password;
}
