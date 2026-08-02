CREATE TABLE otp_codes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target         VARCHAR(255) NOT NULL,
    code_hash      VARCHAR(255) NOT NULL,
    purpose        SMALLINT NOT NULL,
    user_id        UUID REFERENCES users (id),
    attempt_count  INT NOT NULL DEFAULT 0,
    max_attempts   INT NOT NULL DEFAULT 5,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_codes_lookup ON otp_codes (target, purpose, user_id) WHERE used_at IS NULL;
