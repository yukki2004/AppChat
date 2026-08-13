-- Bù migration cho các entity đã commit ở b8c5956 (GroupJoinRequestEntity,
-- GroupAdminPermissionEntity, GroupMemberNicknameEntity, GroupEventEntity, GroupEventRsvpEntity)
-- nhưng chưa có bảng — ddl-auto: validate nên service không start được nếu thiếu.

CREATE TABLE group_join_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id      UUID NOT NULL REFERENCES groups (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    invited_by    UUID REFERENCES users (id),
    status        VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    reviewed_by   UUID REFERENCES users (id),
    reviewed_at   TIMESTAMPTZ,
    reject_reason VARCHAR(200),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 1 user chỉ có đúng 1 request PENDING tại 1 thời điểm cho cùng 1 nhóm.
CREATE UNIQUE INDEX idx_group_join_requests_pending
    ON group_join_requests (group_id, user_id) WHERE status = 'PENDING';

CREATE INDEX idx_group_join_requests_group_pending
    ON group_join_requests (group_id) WHERE status = 'PENDING';

CREATE TABLE group_admin_permissions (
    group_id             UUID NOT NULL REFERENCES groups (id),
    user_id               UUID NOT NULL REFERENCES users (id),
    can_approve_members   BOOLEAN NOT NULL DEFAULT TRUE,
    can_kick_members      BOOLEAN NOT NULL DEFAULT TRUE,
    can_edit_group_info   BOOLEAN NOT NULL DEFAULT TRUE,
    can_manage_events     BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE group_member_nicknames (
    group_id   UUID NOT NULL REFERENCES groups (id),
    user_id    UUID NOT NULL REFERENCES users (id),
    nickname   VARCHAR(50) NOT NULL,
    set_by     UUID NOT NULL REFERENCES users (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE group_events (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                 UUID NOT NULL REFERENCES groups (id),
    title                    VARCHAR(200) NOT NULL,
    location                 VARCHAR(300),
    event_time               TIMESTAMPTZ NOT NULL,
    event_timezone           VARCHAR(50) NOT NULL,
    created_by               UUID NOT NULL REFERENCES users (id),
    reminder_minutes_before  INT NOT NULL,
    is_cancelled             BOOLEAN NOT NULL DEFAULT FALSE,
    reminded_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_group_events_group_id ON group_events (group_id) WHERE is_cancelled = FALSE;

-- Lưới quét reminder (GroupEventEntity#markReminded) — xem CLAUDE.md #3 (chống nhắc trùng).
CREATE INDEX idx_group_events_reminder_due
    ON group_events (event_time) WHERE is_cancelled = FALSE AND reminded_at IS NULL;

CREATE TABLE group_event_rsvp (
    event_id     UUID NOT NULL REFERENCES group_events (id),
    user_id      UUID NOT NULL REFERENCES users (id),
    status       VARCHAR(10) NOT NULL,
    responded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, user_id)
);
