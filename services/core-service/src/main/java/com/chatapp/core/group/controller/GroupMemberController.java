package com.chatapp.core.group.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.group.GroupMemberService;
import com.chatapp.core.group.dto.request.AddMemberRequest;
import com.chatapp.core.group.dto.request.TransferOwnershipRequest;
import com.chatapp.core.group.dto.response.AddMemberResponse;
import com.chatapp.core.group.dto.response.GroupMemberListResponse;

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

    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<GroupMemberListResponse>> listMembers(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(ApiResponse.ok(groupMemberService.listMembers(userId, groupId, cursor)));
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

    @PutMapping("/{groupId}/owner")
    public ResponseEntity<Void> transferOwnership(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        groupMemberService.transferOwnership(userId, groupId, request.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId) {
        groupMemberService.leave(userId, groupId);
        return ResponseEntity.noContent().build();
    }
}
