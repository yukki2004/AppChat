# CLAUDE.md — Chat App Backend Monorepo

> File này là điểm khởi đầu cho bất kỳ AI coding agent nào làm việc trong repo này.
> Đọc file này trước, sau đó đọc `services/<tên-service>/CLAUDE.md` của service đang sửa,
> sau đó tham khảo `skills/` khi cần quy ước cụ thể.
> Spec đầy đủ (schema từng field, enum, luồng nghiệp vụ chi tiết) nằm ở `docs/ChatApp_Architecture_Spec.docx`.
> File này KHÔNG lặp lại schema chi tiết — chỉ cho bối cảnh và quy tắc để code đúng ngay từ đầu.

## Chat App là gì

Nền tảng nhắn tin realtime kiểu Messenger, kiến trúc microservices, 8 service độc lập,
giao tiếp qua REST (client-facing), gRPC (nội bộ, đồng bộ), RabbitMQ (nội bộ, bất đồng bộ),
WebSocket (push realtime tới client).

## 8 service trong hệ thống

| # | Service | Ngôn ngữ | Database | Vai trò chính |
|---|---|---|---|---|
| 1 | api-gateway | Go | Redis | HTTP routing, cookie auth, rate limit, circuit breaker |
| 2 | realtime-gateway | Go | Redis | WS connection pool, fan-out, presence, typing |
| 3 | core-service | Java | PostgreSQL | Auth, profile, friend, block, privacy, quản trị nhóm (role, nickname, file/folder, event) |
| 4 | messaging-service | Java | MongoDB | Chat 1-1/nhóm, thread, poll, tin nhắn tạm thời |
| 5 | notification-service | .NET | PostgreSQL | Push FCM/APNs, in-app, mute, badge |
| 6 | media-service | Go | MongoDB + R2 | Upload chunk, CDN, sticker pack |
| 7 | call-service | .NET | PostgreSQL | Signaling + LiveKit/Agora, không tự xây SFU |
| 8 | social-service | Java | PostgreSQL + MongoDB | Story, post, react, comment, feed |

## Nguyên tắc BẤT DI BẤT DỊCH (đọc kỹ trước khi code)

1. **Database-per-service tuyệt đối.** Không service nào được kết nối thẳng vào DB của service
   khác — kể cả để đọc. Mọi truy cập chéo phải qua gRPC (cần kết quả ngay) hoặc RabbitMQ
   (side-effect không cần chờ). Chi tiết: `skills/database-per-service.md`.
2. **Chọn đúng giao thức cho đúng việc.** REST cho client-facing. gRPC nội bộ khi cần trả lời
   ngay để quyết định bước tiếp theo (verify session, check block). RabbitMQ khi là side-effect
   (notification, fan-out WS, cập nhật presence). Không bao giờ dùng RabbitMQ khi cần response
   đồng bộ, không bao giờ dùng gRPC cho việc có thể làm bất đồng bộ. Chi tiết:
   `skills/service-communication.md`.
3. **Outbox pattern bắt buộc khi publish event.** Mọi service khi cần publish RabbitMQ event
   sau 1 thao tác ghi DB phải insert vào bảng/collection `outbox_events` trong CÙNG transaction
   với thao tác nghiệp vụ, không publish trực tiếp trong code request. Chi tiết:
   `skills/outbox-pattern.md`.
4. **Cookie-based auth, 2 token tách vai trò rõ ràng — không tự ý đổi cơ chế nếu chưa hỏi.**
   `access_token` là JWT (RS256, TTL ngắn ~15 phút, ký bởi Core Service) dùng để service khác tự
   verify chữ ký tại chỗ, không gọi gRPC mỗi request. `refresh_token` vẫn là chuỗi UUID v4 ngẫu
   nhiên (không mã hoá thông tin), lưu Redis + DB TTL rolling 30 ngày, dùng để xin access token
   mới. Cả 2 đều nằm trong cookie HttpOnly riêng biệt. Vì JWT không revoke được tức thời theo bản
   chất, mọi nơi verify access token BẮT BUỘC phải check thêm `cache:jwt_revoked_before:{user_id}`
   (xem `docs/.../05-cookie-auth-flow.md`) để đảm bảo yêu cầu block/logout có hiệu lực ngay lập
   tức — không được bỏ bước này chỉ vì "JWT tự chứa đủ thông tin rồi".
5. **UTC tuyệt đối cho mọi timestamp.** Không lưu giờ local ở backend. Convert sang local chỉ ở
   client lúc render, dựa trên `Intl.DateTimeFormat` hoặc tương đương.
6. **UUID v4 cho mọi PK/FK/event_id**, trừ MongoDB `_id` dùng ObjectId mặc định.
7. **Soft delete mặc định** (`is_deleted` + `deleted_at`), không xoá vật lý trừ khi có lý do rõ
   ràng (VD: R2 file sau retention period).
8. **Không service nào tự ý gọi thẳng API bên thứ 3 thay cho service phụ trách** — VD: chỉ
   `media-service` được gọi Cloudflare R2 API, chỉ `call-service` được gọi LiveKit/Agora API.

## Cấu trúc repo

```
chatapp-backend/
├── CLAUDE.md                  ← file này
├── docs/
│   └── ChatApp_Architecture_Spec.docx   ← spec đầy đủ, schema chi tiết từng field
├── skills/                    ← quy ước dùng chung, đọc khi cần
├── proto/                     ← hợp đồng gRPC dùng chung, quản lý bằng buf
├── services/
│   └── <tên-service>/
│       ├── CLAUDE.md          ← orientation riêng cho service này
│       └── ... (code)
├── docker-compose.yml
└── .github/workflows/
```

## Quy tắc làm việc cho AI coding agent

- Trước khi sửa code 1 service, đọc `services/<tên-service>/CLAUDE.md` trước.
- Trước khi đặt tên DTO/event/bảng/routing key mới, đọc `skills/naming-conventions.md` — không
  tự bịa convention mới dù có vẻ hợp lý.
- Trước khi thêm 1 lời gọi cross-service, đọc `skills/service-communication.md` để chọn đúng
  giao thức, không mặc định dùng REST/HTTP nội bộ giữa các service.
- Trước khi thêm log cho request vào/ra, publish/consume RabbitMQ event, hoặc gọi gRPC, đọc
  `skills/logging-conventions.md` — không tự bịa format log riêng cho từng service.
- Trước khi viết code verify `access_token` (JWT) hoặc code phát hành/xoay vòng key ở Core
  Service, đọc `skills/authentication.md` — không tự parse/verify JWT thủ công.
- Nếu spec (`docs/ChatApp_Architecture_Spec.docx`) và code hiện tại mâu thuẫn nhau, hỏi lại
  người dùng thay vì tự quyết theo 1 trong 2 nguồn.
- Không tự thêm service thứ 9 hoặc gộp/tách service đã chốt mà không hỏi trước.
