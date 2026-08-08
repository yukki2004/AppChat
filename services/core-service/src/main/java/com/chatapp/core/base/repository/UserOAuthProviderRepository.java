package com.chatapp.core.base.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.constant.OAuthProvider;
import com.chatapp.core.base.entity.UserOAuthProviderEntity;

public interface UserOAuthProviderRepository extends JpaRepository<UserOAuthProviderEntity, UUID> {

    Optional<UserOAuthProviderEntity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
