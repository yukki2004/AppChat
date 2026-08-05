-- TODO (not yet built, see UserBlockEntity javadoc): every row today is a "full" block. If a
-- narrower message/call-only block (stays friends/visible, only mutes messaging + calls) gets
-- picked up later, add a `scope SMALLINT NOT NULL DEFAULT 1` column here (FULL=1,
-- MESSAGE_CALL_ONLY=2, per skills/naming-conventions.md #3) and re-check every caller of this
-- table (BlockServiceImpl cascade, FriendServiceImpl.isBlockedEitherDirection).
CREATE TABLE user_blocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id  UUID NOT NULL REFERENCES users (id),
    blocked_id  UUID NOT NULL REFERENCES users (id),
    reason      VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 1 block record per ordered pair — blocking is directional (A blocking B doesn't imply B
-- blocked A), unlike friendships. Also serves both directions of the OR-2-chiều check in
-- FriendServiceImpl.isBlockedEitherDirection() — existsByBlockerIdAndBlockedId(a,b) and
-- existsByBlockerIdAndBlockedId(b,a) are both exact-match lookups on this same composite index,
-- just with swapped literal values.
CREATE UNIQUE INDEX idx_user_blocks_blocker_blocked ON user_blocks (blocker_id, blocked_id);
