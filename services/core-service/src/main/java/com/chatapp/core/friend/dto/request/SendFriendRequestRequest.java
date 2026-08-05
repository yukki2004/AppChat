package com.chatapp.core.friend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendFriendRequestRequest {

    @NotNull
    private UUID addresseeId;

    @Size(max = 200)
    private String message;
}
