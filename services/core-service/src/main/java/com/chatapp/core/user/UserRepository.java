package com.chatapp.core.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsernameAndDeletedAtIsNull(String username);

    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
