package com.chatapp.core.group.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Partial update — {@code null} means "leave unchanged", both fields may be sent together.
 *  {@code avatarUrl} is intentionally not here yet: changing it needs the client to upload
 *  through Media Service first and pass back the resulting CDN URL, and no Media Service client
 *  exists in this codebase yet (see {@code GroupServiceImpl#updateInfo} TODO). */
@Getter
@Setter
public class UpdateGroupInfoRequest {

    @Size(min = 1, max = 100)
    private String name;

    private String description;
}
