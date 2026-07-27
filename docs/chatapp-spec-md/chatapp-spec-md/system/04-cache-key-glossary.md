# **PHỤ LỤC D – CACHE KEY GLOSSARY (Redis, prefix cache:)**

|  |  |  |  |
| :-: | :-: | :-: | :-: |
| **Key Pattern** | **Type** | **TTL** | **Nội dung** |
| cache:refresh_token:{refresh_token} | String JSON | 30 ngày (rolling) | { user_id, device_id... } — index Redis của `user_sessions`, dùng khi `RefreshAccessToken`. Key dùng giá trị gốc (Redis không phải nơi bị đọc trộm hàng loạt như DB backup), nhưng DB (`user_sessions.token_hash`) vẫn chỉ lưu SHA-256 |
| cache:pre_auth:{pre_auth_token} | String JSON | 5 phút | { user_id } — trạng thái trung gian đã verify password, CHƯA verify 2FA. Xem `system/05-cookie-auth-flow.md` E.9 |
| cache:jwt_blacklist:{jti} | String "1" | = thời gian còn lại tới `exp` của token đó | Access token bị revoke khi logout 1 thiết bị |
| cache:jwt_revoked_before:{user_id} | String (timestamp) | 900s (= TTL access token) | Mốc revoke toàn cục khi logout tất cả thiết bị / đổi mật khẩu / admin block — JWT có `iat` <= mốc này bị từ chối |
| cache:user:{user_id} | String JSON | 5 phút | PublicUserDTO |
| cache:user:privacy:{user_id} | String JSON | 10 phút | PrivacySettings |
| cache:friendship:{a}:{b} | String JSON | 2 phút | FriendshipStatus (key sorted a<b) |
| cache:block:{a}:{b} | String "1" | 5 phút | Exists = a blocked b |
| cache:group:{group_id} | String JSON | 5 phút | PublicGroupDTO |
| cache:group:members:{group_id} | String JSON | 2 phút | []UUID member list |
| cache:conversation:list:{user_id} | String JSON | 60s | Danh sách conversation feed |
| cache:message:pin:{conversation_id} | String JSON | 5 phút | Pinned messages list |
| cache:notification:count:{user_id} | Int | 30s | Số notification chưa đọc (badge) |
| cache:presence:{user_id} | String JSON | 35s | { status, last_seen_at } |
| cache:presence:last_seen:{user_id} | String | – | UTC ISO, không expire |
| cache:online:hidden:{user_id} | String "1" | dynamic | User đang trong khung giờ ẩn |
| cache:typing:{conv_id}:{user_id} | String "1" | 3s | Typing indicator |
| cache:ws:user:{user_id} | Set | session | Set{connID} – WS connections của user |
| cache:ws:screen:{user_id}:{screen} | String | session | connID giữ màn hình này |
| cache:ws:room:{conversation_id} | Set | session | Set{connID} – members trong room |
| cache:call:session:{call_id} | String JSON | session | Trạng thái call đang diễn ra |
| cache:media:upload_url:{upload_id} | String | 15 phút | Presigned URL R2 |
| cache:story:list:{user_id} | String JSON | 60s | Stories của user |
| cache:rate_limit:{ip}:{endpoint} | Int | 1 phút | Request count cho rate limit |
| cache:rate_limit:friend_request:{user_id} | Int | 1 ngày | Số lời mời kết bạn đã gửi trong ngày, ngưỡng gợi ý 50 |
| cache:rate_limit:group_create:{user_id} | Int | 1 ngày | Số nhóm đã tạo trong ngày, ngưỡng gợi ý 10 |
| cache:rate_limit:report:{user_id} | Int | 1 ngày | Số report đã gửi trong ngày, chống lạm dụng report |
| cache:friend_request_cooldown:{requester_id}:{addressee_id} | String "1" | 24h | Chặn gửi lại lời mời kết bạn ngay sau khi bị từ chối |
