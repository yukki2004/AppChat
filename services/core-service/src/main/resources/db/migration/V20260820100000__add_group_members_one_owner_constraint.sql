-- idx_group_members_group_user_active (V20260811090000) chỉ chặn 1 user có 2 row active
-- trong cùng 1 group — không chặn 2 user KHÁC NHAU cùng active với role=OWNER trong cùng
-- 1 group. transferOwnership (#13) cần đúng invariant đó: mỗi group tối đa 1 row
-- role=OWNER AND is_active=true tại 1 thời điểm — 2 request transferOwnership chạy đồng
-- thời (2 target khác nhau) sẽ đụng đúng unique index này, request tới sau nhận
-- DataIntegrityViolationException ngay tại saveAndFlush(), dịch thành
-- AppException(GROUP_CONCURRENT_MODIFICATION) — không cần advisory lock riêng.
CREATE UNIQUE INDEX idx_group_members_one_active_owner
    ON group_members (group_id) WHERE role = 'OWNER' AND is_active = TRUE;
