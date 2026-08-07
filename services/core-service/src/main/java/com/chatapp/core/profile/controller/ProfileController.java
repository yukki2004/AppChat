package com.chatapp.core.profile.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.base.UserResponse;
import com.chatapp.core.profile.ProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getPublicProfile(
            @RequestHeader("X-User-Id") UUID viewerId, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getPublicProfile(viewerId, userId)));
    }
}
