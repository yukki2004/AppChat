package com.chatapp.core.block.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatapp.core.base.ApiResponse;
import com.chatapp.core.base.UserResponse;
import com.chatapp.core.block.BlockService;
import com.chatapp.core.block.dto.request.BlockUserRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> block(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody BlockUserRequest request) {
        blockService.block(userId, request.getBlockedId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @RequestHeader("X-User-Id") UUID currentUserId, @PathVariable UUID userId) {
        blockService.unblock(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> listBlocked(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(blockService.listBlocked(userId)));
    }
}
