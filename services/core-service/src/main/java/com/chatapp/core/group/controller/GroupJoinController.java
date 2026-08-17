package com.chatapp.core.group.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.group.GroupJoinService;
import com.chatapp.core.group.dto.request.JoinGroupRequest;
import com.chatapp.core.group.dto.request.RejectJoinRequestRequest;
import com.chatapp.core.group.dto.response.GroupPreviewResponse;
import com.chatapp.core.group.dto.response.JoinGroupResponse;
import com.chatapp.core.group.dto.response.JoinRequestResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupJoinController {

    private final GroupJoinService groupJoinService;

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<GroupPreviewResponse>> preview(@RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.ok(groupJoinService.preview(token)));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinGroupResponse>> joinByToken(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody JoinGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(groupJoinService.joinByToken(userId, request.getToken())));
    }

    @GetMapping("/{groupId}/join-requests")
    public ResponseEntity<ApiResponse<List<JoinRequestResponse>>> listJoinRequests(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupJoinService.listJoinRequests(userId, groupId)));
    }

    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public ResponseEntity<Void> approveJoinRequest(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId, @PathVariable UUID requestId) {
        groupJoinService.approveJoinRequest(userId, groupId, requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectJoinRequest(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID groupId, @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) RejectJoinRequestRequest request) {
        String reason = request == null ? null : request.getReason();
        groupJoinService.rejectJoinRequest(userId, groupId, requestId, reason);
        return ResponseEntity.noContent().build();
    }
}
