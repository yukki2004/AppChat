package com.chatapp.core.base.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatapp.core.base.constant.OtpPurpose;
import com.chatapp.core.base.entity.OtpCodeEntity;

public interface OtpCodeRepository extends JpaRepository<OtpCodeEntity, UUID> {

    Optional<OtpCodeEntity> findTopByTargetAndPurposeAndUserIdAndUsedAtIsNullOrderByCreatedAtDesc(
            String target, OtpPurpose purpose, UUID userId);

    /** Every still-active code for (target, purpose, userId) — used to invalidate all of them
     *  right before a new one is generated, so at most 1 code is ever valid at a time. */
    List<OtpCodeEntity> findByTargetAndPurposeAndUserIdAndUsedAtIsNull(
            String target, OtpPurpose purpose, UUID userId);
}
