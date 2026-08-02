-- Optimistic locking: consuming a backup code reads the whole codes_hash array, removes 1
-- entry in memory, then overwrites the column — 2 concurrent verifies (2 devices logging in
-- at once) can otherwise lose one of the updates (last write wins), letting an already-used
-- code become valid again. `version` lets Hibernate detect that race and reject the losing
-- UPDATE instead of silently overwriting it.
ALTER TABLE two_factor_backup_codes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
