-- Table only — the #26 management endpoints (add/remove/list close friends) aren't built yet,
-- but block (#24) and unfriend (#22) both need to cascade-delete rows here, so the table has to
-- exist before those features land, not after.
CREATE TABLE close_friends (
    user_id     UUID NOT NULL REFERENCES users (id),
    friend_id   UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, friend_id)
);
