package com.chatapp.core.group.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGroupRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String avatarUrl;

    private String description;

    /** Must contain >= 2 ids besides the creator — a group needs >= 3 members total. */
    private List<UUID> memberIds;
}
