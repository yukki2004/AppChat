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
| DTO response | `Public` + PascalCase + `DTO` | `PublicUserDTO`, `PublicMessageDTO` |
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
| DTO response | `Public` + PascalCase + `DTO` | `PublicNotificationDTO` |
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
| DTO struct | PascalCase + `DTO` | `type PublicPresenceDTO struct {}` |

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
