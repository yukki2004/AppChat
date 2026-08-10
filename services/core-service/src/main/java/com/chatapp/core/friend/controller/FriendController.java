package com.chatapp.core.friend.controller;

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
import com.chatapp.core.friend.FriendService;
import com.chatapp.core.friend.dto.request.SendFriendRequestByQrRequest;
import com.chatapp.core.friend.dto.request.SendFriendRequestRequest;
import com.chatapp.core.friend.dto.response.FriendQrTokenResponse;
import com.chatapp.core.friend.dto.response.FriendRequestResponse;
import com.chatapp.core.friend.dto.response.SendFriendRequestResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<SendFriendRequestResponse>> sendRequest(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody SendFriendRequestRequest request) {
        SendFriendRequestResponse result = friendService.sendRequest(userId, request.getAddresseeId(), request.getMessage());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/qr-token")
    public ResponseEntity<ApiResponse<FriendQrTokenResponse>> createQrToken(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.createQrToken(userId)));
    }

    @PostMapping("/requests/qr")
    public ResponseEntity<ApiResponse<SendFriendRequestResponse>> sendRequestByQr(
            @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody SendFriendRequestByQrRequest request) {
        SendFriendRequestResponse result =
                friendService.sendRequestByQrToken(userId, request.getQrToken(), request.getMessage());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/qr-token")
    public ResponseEntity<ApiResponse<Void>> revokeQrToken(@RequestHeader("X-User-Id") UUID userId) {
        friendService.revokeQrToken(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> incoming(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.listIncomingPending(userId)));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> outgoing(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.listOutgoingPending(userId)));
    }

    @PostMapping("/requests/{userId}/accept")
    public ResponseEntity<ApiResponse<Void>> accept(
            @RequestHeader("X-User-Id") UUID currentUserId, @PathVariable UUID userId) {
        friendService.accept(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** #21 — cancel (requester) and reject (addressee) share this one endpoint; which happens
     *  is decided by {@code currentUserId}'s role on the pending row, see {@link FriendService#cancelOrReject}. */
    @DeleteMapping("/requests/{userId}")
    public ResponseEntity<ApiResponse<Void>> cancelOrReject(
            @RequestHeader("X-User-Id") UUID currentUserId, @PathVariable UUID userId) {
        friendService.cancelOrReject(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unfriend(
            @RequestHeader("X-User-Id") UUID currentUserId, @PathVariable UUID userId) {
        friendService.unfriend(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> listFriends(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.listFriends(userId)));
    }

    @PostMapping("/close/{userId}")
    public ResponseEntity<ApiResponse<Void>> addCloseFriend(
            @RequestHeader("X-User-Id") UUID currentUserId, @PathVariable UUID userId) {
        friendService.addCloseFriend(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/close/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeCloseFriend(
            @RequestHeader("X-User-Id") UUID currentUserId, @PathVariable UUID userId) {
        friendService.removeCloseFriend(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/close")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listCloseFriends(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.listCloseFriends(userId)));
    }
}
