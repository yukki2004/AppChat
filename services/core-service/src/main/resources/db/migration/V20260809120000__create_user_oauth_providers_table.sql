-- 1 provider per user (product decision, see docs/.../03-core-service.md 3.4): no account-linking
-- across providers/methods — if the OAuth email already belongs to an existing user, the
-- callback is rejected instead of merging. UNIQUE(user_id) enforces that at the DB level.
CREATE TABLE user_oauth_providers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL UNIQUE REFERENCES users (id),
    provider           VARCHAR(20) NOT NULL,
    provider_user_id   VARCHAR(255) NOT NULL,
    provider_email     VARCHAR(255),
    access_token_enc   TEXT,
    refresh_token_enc  TEXT,
    token_expires_at   TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);
