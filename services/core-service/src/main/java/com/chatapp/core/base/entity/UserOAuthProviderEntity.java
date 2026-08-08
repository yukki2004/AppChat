package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.chatapp.core.base.constant.OAuthProvider;

/** One row per user (UNIQUE user_id) — 1 provider per account by product decision, see
 *  docs/.../03-core-service.md 3.4. access_token_enc/refresh_token_enc/token_expires_at stay
 *  NULL for now: nothing in the current flow calls back into the provider's API after login, so
 *  there's nothing to encrypt yet (see TotpSecretCipher if that ever changes). */
@Entity
@Table(name = "user_oauth_providers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOAuthProviderEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "access_token_enc")
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc")
    private String refreshTokenEnc;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserOAuthProviderEntity(UUID userId, OAuthProvider provider, String providerUserId, String providerEmail) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
    }
}
