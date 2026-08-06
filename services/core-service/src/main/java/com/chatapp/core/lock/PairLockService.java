package com.chatapp.core.lock;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

/**
 * Postgres session-level advisory lock scoped to the current transaction (auto-released on
 * commit/rollback), keyed by an unordered pair of user IDs — order of the arguments doesn't
 * matter, {@code lock(a, b)} and {@code lock(b, a)} contend on the same lock.
 *
 * <h2>The bug this fixes — concrete example</h2>
 * A blocks B, at (almost) the same instant B sends a friend request to A. Read-committed
 * isolation (Postgres default) means neither transaction can see the other's uncommitted writes:
 *
 * <pre>
 *   T1 (A blocks B)                          T2 (B sends request to A)
 *   ---------------                          --------------------------
 *   BEGIN
 *   INSERT INTO user_blocks (A, B)
 *   -- not committed yet --
 *                                            BEGIN
 *                                            SELECT ... FROM user_blocks
 *                                              WHERE (A,B) OR (B,A)
 *                                            -- sees nothing: T1's insert isn't committed --
 *                                            -- "not blocked" -&gt; proceeds --
 *   SELECT ... FROM friendships
 *     WHERE (A,B) unordered
 *   -- no row exists yet (B hasn't inserted
 *      it yet) -&gt; nothing to cascade-delete --
 *   COMMIT  (user_blocks: A blocks B)
 *                                            INSERT INTO friendships (B, A, PENDING)
 *                                            COMMIT  (friendships: B-&gt;A PENDING)
 * </pre>
 * End state: {@code user_blocks} has "A blocks B" AND {@code friendships} has "B→A PENDING" —
 * both transactions individually did the right thing given what they could see, but the combined
 * result violates "a blocked user can't have a friendship". This is a classic **write skew**: 2
 * transactions each read one table, decide based on what they read, then write to a DIFFERENT
 * table/row — so no unique constraint on either table ever fires, and neither side's
 * {@code DataIntegrityViolationException}-catch (used elsewhere for same-row races, see
 * {@code FriendRequestResolver}) has anything to catch.
 *
 * <h2>Why a normal JPA row lock ({@code LockModeType.PESSIMISTIC_WRITE}) can't fix this</h2>
 * JPA locking always targets a row that already exists (it compiles to
 * {@code SELECT ... FOR UPDATE WHERE id = ...}). In the timeline above, at the moment T1 needs to
 * "protect" the {@code friendships} row for (A,B), THAT ROW DOES NOT EXIST YET — B hasn't
 * inserted it. There is nothing to point {@code FOR UPDATE} at. An advisory lock sidesteps this
 * because it locks a plain number chosen by the application (derived from the pair, not from any
 * row), so it can be acquired before any row for that pair exists.
 *
 * <h2>Why this needs a native call, not JPQL</h2>
 * {@code pg_advisory_xact_lock} is a Postgres-only session/transaction function with no entity
 * behind it — JPQL only ever queries/mutates mapped entities, so there is no ORM-level way to
 * express "call this database function and discard the result". A native SQL call is the only
 * option; JPA/Hibernate does not offer an abstraction for this because advisory locks aren't a
 * portable relational concept (MySQL/Oracle have different, incompatible mechanisms).
 *
 * <h2>How it actually prevents the bug</h2>
 * Every method that reads-then-writes {@code friendships}/{@code user_blocks}/{@code close_friends}
 * for a pair calls {@code lock(a, b)} as the FIRST thing, before any read. In the timeline above,
 * if T2 must acquire this lock before its {@code SELECT ... FROM user_blocks} check, and T1
 * already holds it (acquired right when T1 started), T2 simply blocks and waits — it cannot run
 * its check until T1 commits (releasing the lock automatically). Once T2 resumes, it re-reads
 * {@code user_blocks} and NOW sees T1's committed block row, so it correctly rejects the friend
 * request instead of creating the orphaned {@code friendships} row.
 */
@Component
public class PairLockService {

    @PersistenceContext
    private EntityManager entityManager;

    public void lock(UUID userA, UUID userB) {
        boolean aFirst = userA.compareTo(userB) <= 0;
        UUID lo = aFirst ? userA : userB;
        UUID hi = aFirst ? userB : userA;
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1, ?2)")
                .setParameter(1, lo.hashCode())
                .setParameter(2, hi.hashCode())
                .getSingleResult();
    }
}
