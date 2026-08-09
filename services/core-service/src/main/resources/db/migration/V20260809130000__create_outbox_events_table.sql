-- Outbox pattern (skills/outbox-pattern.md) — every RabbitMQ publish that follows a business DB
-- write goes through this table in the SAME transaction, never a direct publish. A separate Go
-- relay worker (services/core-service/outbox-relay/) polls status=PENDING rows and publishes.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       UUID NOT NULL UNIQUE,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    payload        JSONB NOT NULL,
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count    INT NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- The relay worker's core query is "give me PENDING rows, oldest first" — index it directly
-- instead of scanning the whole table every poll cycle.
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at) WHERE status = 'PENDING';
