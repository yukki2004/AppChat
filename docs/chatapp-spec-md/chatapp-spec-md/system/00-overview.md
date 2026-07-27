# **0. TỔNG QUAN HỆ THỐNG**

## **0.1 Giới thiệu**

Chat App là nền tảng nhắn tin realtime tầm cỡ Facebook Messenger (mini), được xây dựng theo kiến trúc microservices với 8 service độc lập. Hệ thống áp dụng:

  - Cookie-based Authentication: HttpOnly Secure SameSite=Strict cookie, KHÔNG dùng Authorization Bearer header. 2 token tách vai trò: access_token (JWT RS256, TTL 15 phút, verify tại chỗ không cần gRPC) + refresh_token (UUID v4 ngẫu nhiên, TTL rolling 30 ngày, lưu Redis+DB, dùng xin access_token mới). Chi tiết: Phụ lục E.
  - Outbox Pattern nội bộ mỗi service: mỗi service có bảng outbox_events riêng trong DB của mình, Go relay worker poll và publish RabbitMQ.
  - Deduplication: mỗi service consumer có bảng received_event_dedup với UNIQUE(event_id) để chống xử lý trùng.
  - Tất cả timestamp lưu UTC. Client tự convert về local timezone khi hiển thị.
  - Cloudflare R2 + CDN cho toàn bộ file media. Upload chunked trực tiếp client → R2.

## **0.2 Danh sách Service**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Service** | **Lang** | **Database** | **Vai trò chính** |
| 1 | API Gateway | Go | Redis | HTTP routing, cookie auth, rate limit, circuit breaker |
| 2 | Realtime Gateway | Go | Redis | WS connection pool, realtime fan-out, online/away/offline, typing indicator |
| 3 | Core Service | Java | PostgreSQL | Auth, OAuth2, 2FA, Profile, Friend, Block, Privacy, quản trị nhóm (role, invite, nickname, file/folder, event) |
| 4 | Messaging Service | Java | MongoDB | Chat 1-1/nhóm, seen, unsend, pin, pending msg, Thread, Poll, tin nhắn tạm thời |
| 5 | Notification Service | .NET | PostgreSQL | Push FCM/APNs, in-app, mute, badge |
| 6 | Media Service | Go | MongoDB + R2 | Upload chunk, CDN streaming, thumbnail, virus scan, sticker pack |
| 7 | Call Service | .NET | PostgreSQL | Signaling + tích hợp LiveKit/Agora, gọi 1-1 & nhóm, share màn hình |
| 8 | Social Service | Java | PostgreSQL | Story, Post, React, Comment, Feed, Tag |

## 

## **0.3 Luồng request tổng thể**

### **HTTP REST Request**

Client  →  [Cookie: access_token=<jwt>]

        →  API Gateway (Go)

               ↓ verify chữ ký JWT tại chỗ (public key Core Service) + check Redis revoke — không gRPC

               ↓ forward HTTP (với X-User-Id header)

        →  Target Service (Identity / Messaging / Group / Social / Media / Call)

               ↓ xử lý nghiệp vụ

               ↓ insert outbox_events (same DB transaction)

               ↓ Go Relay Worker poll outbox → publish RabbitMQ

        ←  JSON Response

### **WebSocket Realtime**

Client  →  WS Handshake + Cookie access_token

        →  WS Gateway (Go)

               ↓ verify chữ ký JWT tại chỗ + check Redis revoke — không gRPC

               ↓ register connection: cache:ws:user:{id}

               ↓ client sends: {"action":"screen_register","screen":"chat:convId"}

               ↓ Subscribe Redis Pub/Sub channel: user:{id}

        ←  Server push events (message.new, presence.update, typing...)

### **Upload Media (Chunked, không chờ xong)**

Client  →  POST /media/upload/init → Media Service → presigned R2 URLs

        →  PUT chunk 1..N → Cloudflare R2 (bypass backend)

        →  POST /media/upload/complete → Media Service → R2 CompleteMultipart

               ↓ publish media.upload_completed → Messaging update cdn_url

        ←  WS push: message.update (cdn_url sẵn sàng, hiển thị ngay)

## **0.4 Giao tiếp giữa các service**

|  |  |  |
| :-: | :-: | :-: |
| **Loại** | **Protocol** | **Dùng khi** |
| **Đồng bộ (sync)** | gRPC | Cần response ngay: verify session, check friendship, check block, get group members |
| **Bất đồng bộ (async)** | RabbitMQ | Side effects: gửi notification, update presence, fan-out WS event, reindex search |
| **Realtime push** | Redis Pub/Sub | WS Gateway fan-out event đến đúng connection của user |
| **File transfer** | Cloudflare R2 | Upload/download media – client làm việc trực tiếp với R2 sau khi có presigned URL |

## **0.5 Quy ước chung toàn hệ thống**

|  |  |  |
| :-: | :-: | :-: |
| **Loại** | **Quy tắc** | **Ví dụ** |
| **Timestamp** | UTC tuyệt đối, TIMESTAMPTZ (PG) / ISODate (Mongo) | 2026-05-17T10:30:00Z |
| **UUID** | v4 – tất cả PK, FK, event_id | 550e8400-e29b-41d4-a716-446655440000 |
| **Cache Redis key** | Bắt đầu bằng cache: | cache:user:{id}, cache:group:{id} |
| **DTO public** | Bắt đầu bằng Public (class/record name) | PublicUserDTO, PublicMessageDTO |
| **Message type enum** | Bắt đầu bằng MESSAGE_ | MESSAGE_TEXT, MESSAGE_VIDEO, MESSAGE_FILE |
| **RabbitMQ routing key** | domain.action | message.sent, user.blocked, call.ended |
| **WS screen key** | screen:{name}:{id?} | screen:chat:abc123, screen:home |
| **Event ID (dedup)** | UUID v4 bất biến, publisher tạo | Dùng làm idempotency key trong outbox |
| **Cloudflare CDN URL** | cdn.{domain}/{key}?params | cdn.app.com/img/abc.jpg?w=400&fit=cover |
| **Soft delete** | is_deleted=true + deleted_at | Không xoá vật lý dữ liệu |
| **Pagination** | Cursor-based (before_id / after_id) | ?before_id=abc&limit=30 |
| **Error response** | { error_code, message, request_id } | { "error_code": "BLOCK_EXISTS", ... } |
