package com.chatapp.core.group.util;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

/**
 * Postgres advisory lock scoped to the current transaction (auto-released on commit/rollback),
 * keyed by {@code groupId} — serializes mutations on the same group (role change, member count)
 * that would otherwise write-skew past row-level locking (2 concurrent {@code transferOwnership}
 * calls each read "actor is OWNER" before either commits, so both pass the check and both write —
 * see {@code GroupMemberServiceImpl#transferOwnership}).
 *
 * <p>Non-blocking ({@code pg_try_advisory_xact_lock}, not {@code pg_advisory_xact_lock}) — group
 * mutations collide rarely enough that failing the second caller fast with a clear "try again" is
 * simpler than a retry loop (optimistic locking) and doesn't make an unrelated caller wait on the
 * DB for the length of someone else's transaction.
 */
@Component
public class GroupLockService {

    @PersistenceContext
    private EntityManager entityManager;

    public void tryLock(UUID groupId) {
        Boolean acquired = (Boolean) entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(?1)")
                .setParameter(1, groupId.getMostSignificantBits())
                .getSingleResult();
        if (!Boolean.TRUE.equals(acquired)) {
            throw new AppException(ErrorCode.GROUP_CONCURRENT_MODIFICATION);
        }
    }
}
