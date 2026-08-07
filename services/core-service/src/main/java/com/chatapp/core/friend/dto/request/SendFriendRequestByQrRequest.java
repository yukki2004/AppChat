package com.chatapp.core.friend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendFriendRequestByQrRequest {

    @NotBlank
    private String qrToken;

    @Size(max = 200)
    private String message;
}
