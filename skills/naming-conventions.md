# Skill: Naming Conventions

> Áp dụng khi: đặt tên class/DTO/entity/enum trong bất kỳ service nào, đặt tên RabbitMQ
> routing key, đặt tên gRPC method/message, đặt tên bảng/collection/field DB, đặt tên
> Redis cache key.
>
> Nguyên tắc chung: convention khác nhau theo NGÔN NGỮ của service, nhưng convention cho
> event/routing key/cache key thì THỐNG NHẤT toàn hệ thống bất kể service viết bằng ngôn ngữ gì.

## 1. RabbitMQ — routing key & exchange (thống nhất toàn hệ thống)

- Routing key luôn dạng `domain.action`, chữ thường, gạch dưới nếu action nhiều từ.
  Đúng: `message.sent`, `friend.request_sent`, `group.member_removed`, `call.participant_update`.
  Sai: `MessageSent`, `message-sent`, `sentMessage`.
- Exchange đặt theo domain, dạng `{domain}.exchange`: `chat.exchange`, `user.exchange`,
  `group.exchange`, `presence.exchange`, `notification.exchange`, `call.exchange`,
  `social.exchange`, `media.exchange`.
- Event publish bởi service nào thì domain đó do service đó sở hữu — không service khác được
  publish vào exchange không phải của mình (VD: chỉ Messaging Service publish vào `chat.exchange`).

## 2. Event ID / idempotency key

- Mọi event có `event_id` là UUID v4, do PUBLISHER tạo ra (không phải consumer), dùng làm
  idempotency key trong bảng `received_event_dedup` phía consumer.
- Client-generated ID (VD: `message_id` khi gửi tin nhắn) cũng là UUID v4, tạo phía client
  TRƯỚC khi gọi API, dùng để chống gửi trùng khi client retry.

## 3. Cache key Redis (thống nhất toàn hệ thống)

- Luôn bắt đầu bằng `cache:`.
- Dạng `cache:{entity}:{id}` hoặc `cache:{entity}:{sub}:{id}`.
  Đúng: `cache:user:{id}`, `cache:session:{session_id}`, `cache:ws:room:{conversation_id}`,
  `cache:presence:{user_id}`, `cache:rate_limit:{ip}:{endpoint}`.
- TTL luôn khai báo rõ trong code, không để mặc định vô hạn trừ khi có lý do (VD:
  `cache:presence:last_seen:{user_id}` không expire).

## 4. Java (Core, Messaging, Social Service)

| Loại | Convention | Ví dụ |
|---|---|---|
| Entity (JPA) | PascalCase + suffix `Entity` | `UserEntity`, `FriendshipEntity` |
| MongoDB Document | PascalCase + suffix `Document` | `MessageDocument`, `ConversationDocument` |
| DTO request | PascalCase + suffix `Request` | `CreateUserRequest`, `SendMessageRequest` |
| DTO response (REST thường) | PascalCase + suffix `Response` | `UserResponse`, `MessageResponse` |
| DTO bắn ra WebSocket | PascalCase + suffix `Public` | `UserPublic`, `MessagePublic` |
| DTO dùng để cache (Redis) | PascalCase + suffix `Cache` | `UserCache`, `SessionCache` |
| Service class | PascalCase + suffix `Service` | `AuthService`, `MessageService` |
| Repository | PascalCase + suffix `Repository` | `UserRepository`, `MessageRepository` |
| Controller | PascalCase + suffix `Controller` | `AuthController`, `MessageController` |
| gRPC service impl | PascalCase + suffix `GrpcService` | `IdentityGrpcService` |
| Enum | PascalCase (class), UPPER_SNAKE (values) | `enum FriendshipStatus { PENDING, ACCEPTED }` |
| Method | camelCase | `sendMessage()`, `verifySession()` |
| Field private | camelCase | `private String sessionId;` |
| Package | lowercase, dot-separated | `com.chatapp.messaging.service` |

## 5. .NET / C# (Notification, Call Service)

| Loại | Convention | Ví dụ |
|---|---|---|
| Entity (EF Core) | PascalCase + suffix `Entity` | `NotificationEntity` |
| DTO request | PascalCase + suffix `Request` | `RegisterDeviceTokenRequest` |
| DTO response (REST thường) | PascalCase + suffix `Response` | `NotificationResponse` |
| DTO bắn ra WebSocket | PascalCase + suffix `Public` | `NotificationPublic` |
| DTO dùng để cache (Redis) | PascalCase + suffix `Cache` | `DeviceTokenCache` |
| Service class | PascalCase + suffix `Service` | `NotificationService` |
| Interface | `I` + PascalCase | `IPushProvider` |
| Controller | PascalCase + suffix `Controller` | `CallController` |
| Enum | PascalCase (type + values) | `enum NotificationType { MessageNew, CallIncoming }` |
| Method | PascalCase | `SendPushAsync()`, `CreateCallSession()` |
| Property | PascalCase | `public bool IsRead { get; set; }` |
| Field private | `_camelCase` | `private readonly ILogger _logger;` |
| Namespace | PascalCase.dot.separated | `ChatApp.Notification.Application.Services` |

## 6. Go (API Gateway, Realtime Gateway, Media Service)

| Loại | Convention | Ví dụ |
|---|---|---|
| Struct exported | PascalCase | `type GatewayConfig struct {}` |
| Struct internal | PascalCase, package lowercase che phạm vi | `type connectionEntry struct {}` |
| Interface | PascalCase, thường suffix `-er` | `type PushSender interface { Send() error }` |
| Function exported | PascalCase | `func NewPresenceService(...) *PresenceService` |
| Function internal | camelCase | `func parseScreenKey(raw string) string` |
| Variable | camelCase | `sessionID`, `userID`, `connID` |
| Package | lowercase, 1 từ | `package middleware`, `package handler` |
| File | snake_case.go | `presence_service.go`, `cookie_auth.go` |
| DTO response (REST thường) | PascalCase + suffix `Response` | `type PresenceResponse struct {}` |
| DTO bắn ra WebSocket | PascalCase + suffix `Public` | `type PresencePublic struct {}` |
| DTO dùng để cache (Redis) | PascalCase + suffix `Cache` | `type PresenceCache struct {}` |

## 7. Database — table/collection/field

- Table Postgres: snake_case số nhiều — `users`, `group_members`, `group_file_folders`.
- Collection Mongo: snake_case số nhiều — `messages`, `poll_votes`, `sticker_packs`.
- Field: snake_case — `created_at`, `is_deleted`, `media_upload_id`.
- FK field: `{tên_bảng_số_ít}_id` — `user_id`, `group_id`, `message_id`.
- Boolean field: tiền tố `is_`/`has_` — `is_deleted`, `is_pinned`, `has_reacted`.
- Timestamp: hậu tố `_at` — `created_at`, `expires_at`, `reminded_at`.

## 8. gRPC (proto)

- Package proto: `chatapp.{domain}.v1` — `chatapp.identity.v1`.
- Service name: PascalCase + `Service` — `IdentityService`, `GroupService`.
- RPC method: PascalCase, động từ đầu câu — `VerifySession`, `CheckFriendship`, `GetGroupMembers`.
- Message request/response: tên method + `Request`/`Response` — `VerifySessionRequest`,
  `VerifySessionResponse`.

## 9. Cloudflare R2 object key (thống nhất toàn hệ thống, chỉ Media Service ghi key này)

> Áp dụng khi: Media Service sinh `r2_key` lúc khởi tạo upload session (mục 6.1 #1,
> `06-media-service.md`). Đọc mục này TRƯỚC KHI đổi format `r2_key` — sai format ở đây rất khó
> sửa về sau vì key được xem là bất biến sau khi tạo (xem quy tắc #3 bên dưới).

**2 nhánh format, rẽ theo `context_type`:**

- `context_type = message` (file gửi trong 1 conversation — DIRECT/GROUP/SELF đều tính, xem
  `04-messaging-service.md` mục 4.12/4.13):

  ```
  message/{conversation_id}/{yyyy}/{mm}/{conversation_type}/{message_id}/{upload_id}/{variant}.{ext}
  ```

  VD: `message/conv_7a1.../2026/07/GROUP/msg_9f2.../upl_c81.../original.jpg`

- `context_type ∈ {avatar, cover, post, story, sticker}` (không thuộc conversation nào):

  ```
  {context_type}/{yyyy}/{mm}/{context_id}/{upload_id}/{variant}.{ext}
  ```

  VD: `avatar/2026/07/user_5f0.../upl_1a2.../original.jpg`

**Ý nghĩa từng token:**

| Token | Lấy từ đâu | Quy tắc |
|---|---|---|
| `context_type` | Field `context_type` có sẵn trong `media_uploads` | Chữ thường, đúng 1 trong 6 giá trị: `message`, `avatar`, `cover`, `post`, `story`, `sticker` — không tự thêm giá trị khác |
| `conversation_id` | `messages.conversation_id` | Chỉ có ở nhánh `message`. Cố định vĩnh viễn vì 1 message không bao giờ đổi conversation (forward tạo message MỚI ở conversation khác, không di chuyển message cũ — xem `forwarded_from_id` mục 4.4) nên đưa vào key không vi phạm tính bất biến |
| `conversation_type` | `conversations.type` | `DIRECT` / `GROUP` / `SELF` — chèn giữa để nhận diện nhanh lúc debug/ops mà không cần query lại DB |
| `yyyy`/`mm` | `created_at` | 4 số / 2 số đệm `0`, dùng làm prefix cho R2 lifecycle rule (retention/dọn rác) |
| `context_id` | Field `context_id` có sẵn | message_id / user_id (avatar, cover) / post_id / story_id / sticker_pack_id |
| `upload_id` | Field `upload_id` có sẵn | UUID v4 client-gen, đúng quy ước mục 2 |
| `variant` | Không phải field DB, chỉ hậu tố lúc ghi key | Cố định 1 trong 3: `original` / `thumb` / `hls` |
| `ext` | Map từ `mime_type` qua bảng whitelist cố định | KHÔNG lấy từ `original_file_name` do user đặt |

**Quy tắc cứng:**

1. Toàn bộ token chữ thường, phân cách bằng `/` — khác `.` của routing key (mục 1) và `:` của
   cache key (mục 3), không lẫn lộn ký tự phân cách giữa 3 loại key này.
2. Không bao giờ đưa `original_file_name`, tên folder, hay bất kỳ chuỗi do user tự gõ vào key —
   chỉ ghép từ UUID/enum/số. Tránh path traversal và ký tự đặc biệt không hợp lệ với R2/S3 API.
3. Key sinh ra đúng 1 lần lúc `status=PENDING` và **bất biến vĩnh viễn** sau đó. Mọi thao tác
   sau này (rename/move folder logic ở `conversation_folders`, sửa message) chỉ sửa dữ liệu tham
   chiếu trong DB, không bao giờ ghi lại hay đổi key này.
4. Phân môi trường dev/stg/prod bằng **bucket riêng cho mỗi môi trường** (khớp
   `.env.dev`/`.env.stg`/`.env.prod.example` hiện có), không nhét token môi trường vào key.
