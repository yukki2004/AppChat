package com.chatapp.core.block.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlockUserRequest {

    @NotNull
    private UUID blockedId;

    @Size(max = 100)
    private String reason;
}
