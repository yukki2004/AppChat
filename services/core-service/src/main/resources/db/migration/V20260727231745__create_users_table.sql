CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username           VARCHAR(50) UNIQUE NOT NULL,
    email              VARCHAR(255) UNIQUE,
    email_verified_at  TIMESTAMPTZ,
    pending_email      VARCHAR(255),
    phone              VARCHAR(20) UNIQUE,
    phone_verified_at  TIMESTAMPTZ,
    password_hash      VARCHAR(255),
    display_name       VARCHAR(100) NOT NULL,
    avatar_url         VARCHAR(500),
    cover_url          VARCHAR(500),
    bio                TEXT,
    date_of_birth      DATE,
    is_private         BOOLEAN NOT NULL DEFAULT false,
    is_active          BOOLEAN NOT NULL DEFAULT true,
    is_blocked         BOOLEAN NOT NULL DEFAULT false,
    blocked_reason     TEXT,
    blocked_at         TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_phone ON users (phone) WHERE deleted_at IS NULL;
