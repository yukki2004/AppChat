package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_ref", length = 24)
    private String conversationRef;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    private String description;

    @Column(name = "invite_link_token", unique = true, length = 100)
    private String inviteLinkToken;

    @Column(name = "invite_link_expires_at")
    private Instant inviteLinkExpiresAt;

    @Column(name = "qr_code_token", unique = true, length = 100)
    private String qrCodeToken;

    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval;

    @Column(name = "only_admin_can_send", nullable = false)
    private boolean onlyAdminCanSend;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GroupEntity(String name, String avatarUrl, String description, UUID createdBy) {
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.description = description;
        this.createdBy = createdBy;
        this.requireApproval = false;
        this.onlyAdminCanSend = false;
        this.maxMembers = 500;
        this.memberCount = 0;
        this.isDeleted = false;
    }

    public void setConversationRef(String conversationRef) {
        this.conversationRef = conversationRef;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeAvatar(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }

    public void setOnlyAdminCanSend(boolean onlyAdminCanSend) {
        this.onlyAdminCanSend = onlyAdminCanSend;
    }

    public void rotateInviteLink(String token, Instant expiresAt) {
        this.inviteLinkToken = token;
        this.inviteLinkExpiresAt = expiresAt;
    }

    public void revokeInviteLink() {
        this.inviteLinkToken = null;
        this.inviteLinkExpiresAt = null;
    }

    public void rotateQrCode(String token) {
        this.qrCodeToken = token;
    }

    public void incrementMemberCount() {
        this.memberCount++;
    }

    public void decrementMemberCount() {
        this.memberCount = Math.max(0, this.memberCount - 1);
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = Instant.now();
    }
}
