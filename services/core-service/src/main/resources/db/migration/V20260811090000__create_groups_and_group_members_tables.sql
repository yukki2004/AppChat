CREATE TABLE groups (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_ref        VARCHAR(24),
    name                    VARCHAR(100) NOT NULL,
    avatar_url              VARCHAR(500),
    description             TEXT,
    invite_link_token       VARCHAR(100) UNIQUE,
    invite_link_expires_at  TIMESTAMPTZ,
    qr_code_token           VARCHAR(100) UNIQUE,
    require_approval        BOOLEAN NOT NULL DEFAULT FALSE,
    only_admin_can_send     BOOLEAN NOT NULL DEFAULT FALSE,
    max_members             INT NOT NULL DEFAULT 500,
    member_count            INT NOT NULL DEFAULT 0,
    created_by              UUID NOT NULL REFERENCES users (id),
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- conversation_ref tạm nullable — chưa có gRPC client gọi MessagingService.CreateConversation
-- trong codebase, cột này sẽ được set bằng 1 UPDATE ngay sau khi gRPC đó được nối, không set
-- được lúc insert. Xem GroupServiceImpl.createGroup().
CREATE INDEX idx_groups_created_by ON groups (created_by) WHERE is_deleted = FALSE;

CREATE TABLE group_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups (id),
    user_id     UUID NOT NULL REFERENCES users (id),
    role        VARCHAR(10) NOT NULL,
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    added_by    UUID REFERENCES users (id),
    left_at     TIMESTAMPTZ,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

-- 1 user chỉ có đúng 1 row active trong 1 group tại 1 thời điểm — chặn add trùng khi 2 request
-- addMember chạy đồng thời cho cùng (group_id, user_id).
CREATE UNIQUE INDEX idx_group_members_group_user_active
    ON group_members (group_id, user_id) WHERE is_active = TRUE;

CREATE INDEX idx_group_members_user_active ON group_members (user_id) WHERE is_active = TRUE;
