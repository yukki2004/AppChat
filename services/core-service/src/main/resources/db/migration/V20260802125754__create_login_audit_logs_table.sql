CREATE TABLE login_audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users (id),
    event_type      VARCHAR(30) NOT NULL,
    ip_address      INET,
    user_agent      TEXT,
    device_id       VARCHAR(100),
    session_id      UUID,
    failure_reason  VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_audit_logs_user_id ON login_audit_logs (user_id);
