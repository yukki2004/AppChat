package com.chatapp.core.group.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.group.GroupService;
import com.chatapp.core.group.dto.request.CreateGroupRequest;
import com.chatapp.core.group.dto.request.UpdateGroupInfoRequest;
import com.chatapp.core.group.dto.response.GroupInfoResponse;
import com.chatapp.core.group.dto.response.GroupInviteLinkResponse;
import com.chatapp.core.group.dto.response.GroupQrCodeResponse;
import com.chatapp.core.group.dto.response.GroupResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse result = groupService.createGroup(
                userId, request.getName(), request.getAvatarUrl(), request.getDescription(), request.getMemberIds());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupInfoResponse>> updateInfo(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupInfoRequest request) {
        GroupInfoResponse result = groupService.updateInfo(userId, groupId, request.getName(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{groupId}/qr-code")
    public ResponseEntity<ApiResponse<GroupQrCodeResponse>> generateQrCode(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.generateQrCode(userId, groupId)));
    }

    @PostMapping("/{groupId}/qr-code/reset")
    public ResponseEntity<ApiResponse<GroupQrCodeResponse>> resetQrCode(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.resetQrCode(userId, groupId)));
    }

    @PostMapping("/{groupId}/invite-link")
    public ResponseEntity<ApiResponse<GroupInviteLinkResponse>> generateInviteLink(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.generateInviteLink(userId, groupId)));
    }

    @PostMapping("/{groupId}/invite-link/reset")
    public ResponseEntity<ApiResponse<GroupInviteLinkResponse>> resetInviteLink(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.resetInviteLink(userId, groupId)));
    }
}
