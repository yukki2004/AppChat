-- Multiple 2FA methods can be registered simultaneously per user (TOTP + SMS + EMAIL),
-- so one row per (user_id, method) instead of one row per user.
CREATE TABLE two_factor_methods (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users (id),
    method             VARCHAR(10) NOT NULL,
    totp_secret_enc    VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, method)
);

CREATE INDEX idx_two_factor_methods_user_id ON two_factor_methods (user_id);

-- Backup codes are an account-level fallback, not tied to any one method.
CREATE TABLE two_factor_backup_codes (
    user_id            UUID PRIMARY KEY REFERENCES users (id),
    codes_hash         TEXT[] NOT NULL,
    codes_used         INT NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
