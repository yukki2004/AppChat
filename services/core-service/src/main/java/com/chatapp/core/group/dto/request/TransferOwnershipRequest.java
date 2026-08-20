package com.chatapp.core.group.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferOwnershipRequest {

    @NotNull
    private UUID userId;
}
