package com.chatapp.core.group.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectJoinRequestRequest {

    @Size(max = 200)
    private String reason;
}
