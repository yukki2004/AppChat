CREATE TABLE friendships (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id   UUID NOT NULL REFERENCES users (id),
    addressee_id   UUID NOT NULL REFERENCES users (id),
    status         SMALLINT NOT NULL DEFAULT 1,
    message        VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Chuẩn hoá cặp (requester_id, addressee_id) không phân biệt chiều — chặn A và B cùng lúc gửi
-- lời mời cho nhau tạo ra 2 row PENDING song song. Xem docs/.../03-core-service.md mục 3.4.
CREATE UNIQUE INDEX idx_friendships_unordered_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));

CREATE INDEX idx_friendships_addressee_status ON friendships (addressee_id, status);
CREATE INDEX idx_friendships_requester_status ON friendships (requester_id, status);

-- FriendshipRepository.findByUnorderedPair() filters directly on (requester_id, addressee_id)
-- in both directions via OR — the unique index above uses LEAST/GREATEST so Postgres can't use
-- it for this query shape. This plain index serves both OR branches (called on every
-- sendRequest/accept, the hottest path in this feature).
CREATE INDEX idx_friendships_requester_addressee ON friendships (requester_id, addressee_id);
