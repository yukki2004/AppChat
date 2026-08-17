package com.chatapp.core.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinGroupRequest {

    @NotBlank
    private String token;
}
