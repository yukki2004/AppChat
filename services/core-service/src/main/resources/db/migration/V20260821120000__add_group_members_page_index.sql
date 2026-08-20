-- listMembers() keyset-pages active members ordered by (joined_at, id); the only existing
-- indexes on group_members are (group_id, user_id) and (user_id), neither of which supports
-- this ORDER BY. Needed now that groups can grow past the default 500-member cap.
CREATE INDEX idx_group_members_group_active_joined
    ON group_members (group_id, joined_at, id) WHERE is_active = TRUE;
