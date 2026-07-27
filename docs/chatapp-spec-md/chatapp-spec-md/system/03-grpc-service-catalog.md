# **PHỤ LỤC C – gRPC SERVICE CATALOG**

Tất cả gRPC call là internal (service-to-service), không expose ra ngoài internet.

> Lưu ý: xác thực `access_token` (JWT) mỗi request KHÔNG còn qua gRPC — API Gateway/WS Gateway
> tự verify chữ ký JWT tại chỗ bằng public key. gRPC vào IdentityService chỉ cần khi
> `RefreshAccessToken` (access token hết hạn) hoặc các nghiệp vụ khác vốn đã cần gRPC bên dưới.
> Chi tiết: `system/05-cookie-auth-flow.md`.

|  |  |  |  |
| :-: | :-: | :-: | :-: |
| **Service** | **Method (signature)** | **Caller** | **Mục đích** |
| **IdentityService** | RefreshAccessToken(refresh_token) → { access_token, refresh_token } | API GW (forward từ `/auth/refresh`) | Cấp access_token JWT mới khi hết hạn, xoay vòng refresh_token. Xem `system/05-cookie-auth-flow.md` |
| **IdentityService** | GetUserPublicInfo(user_id) → PublicUserDTO | Nhiều service | Lấy thông tin cơ bản user |
| **IdentityService** | CheckFriendship(a, b) → FriendshipStatus | Messaging, Social | Kiểm tra quan hệ kết bạn |
| **IdentityService** | CheckBlock(a, b) → bool | Messaging, Social | Kiểm tra chặn nhau |
| **IdentityService** | GetPrivacySettings(user_id) → PrivacyDTO | Presence, Social | Lấy cài đặt privacy |
| **GroupService** | GetGroupMembers(group_id) → []UUID | Messaging, WS GW | Lấy danh sách thành viên |
| **GroupService** | CheckGroupRole(user_id, group_id) → GroupRole | Messaging, Call | Kiểm tra quyền trong nhóm |
| **GroupService** | GetGroupInfo(group_id) → PublicGroupDTO | Messaging, Notification | Thông tin nhóm |
| **PresenceService** | GetPresence(user_ids[]) → []PublicPresenceDTO | Identity (friend list) | Trạng thái online batch |
| **MessagingService** | CreateConversation(type=GROUP, participant_ids[]) → conversation_id | Core Service (GroupService, lúc tạo nhóm mới) | Tạo phòng chat cho nhóm mới — Core lưu kết quả vào `groups.conversation_ref`. Gọi ĐỒNG BỘ vì `conversation_ref` là NOT NULL, phải có ngay lúc tạo nhóm xong |
| **MediaService** | GetUploadURL(upload_id) → PresignedURLs | API GW | Presigned URL upload R2 |
| **NotificationService** | SendVoIPPush(user_id, payload) | Call Svc | Push cuộc gọi đến (ưu tiên cao) |
