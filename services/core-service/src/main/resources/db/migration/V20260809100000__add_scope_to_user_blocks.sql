-- Decided 2026-08-09: block v1 is message/call-only (mutes messaging + calls; does NOT touch
-- friendships/close_friends, does NOT block friend requests, does NOT hide the profile).
-- Enforcement lives in messaging-service/call-service — not built yet, see TODO in
-- docs/.../03-core-service.md #24. `scope` says WHICH of messaging/calls is muted
-- (MESSAGE/CALL/ALL, stored as plain text — no numeric code, see BlockScope javadoc). v1 only
-- ever inserts ALL; picking just one is a future UI choice.
ALTER TABLE user_blocks ADD COLUMN scope VARCHAR(10) NOT NULL DEFAULT 'ALL';
