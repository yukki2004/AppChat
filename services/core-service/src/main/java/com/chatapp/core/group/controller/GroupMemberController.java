package com.chatapp.core.group.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.group.GroupMemberService;
import com.chatapp.core.group.dto.request.AddMemberRequest;
import com.chatapp.core.group.dto.response.AddMemberResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    @PostMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<AddMemberResponse>> addMember(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(groupMemberService.addMember(userId, groupId, request.getUserId())));
    }

    @DeleteMapping("/{groupId}/members/{targetUserId}")
    public ResponseEntity<Void> kickMember(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId, @PathVariable UUID targetUserId) {
        groupMemberService.kickMember(userId, groupId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/admins/{targetUserId}")
    public ResponseEntity<Void> promoteToAdmin(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId, @PathVariable UUID targetUserId) {
        groupMemberService.promoteToAdmin(userId, groupId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/admins/{targetUserId}")
    public ResponseEntity<Void> demoteToMember(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId, @PathVariable UUID targetUserId) {
        groupMemberService.demoteToMember(userId, groupId, targetUserId);
        return ResponseEntity.noContent().build();
    }
}
