# CLAUDE.md — Notification Service

> Đọc `/CLAUDE.md` ở root trước.

## Vai trò

Push notification (FCM/APNs/Web Push) và in-app notification cho gần như mọi sự kiện trong hệ
thống. Service này chỉ consume — không có client nào gọi trực tiếp trừ đọc danh sách
notification/mark-read.

## Tech stack

.NET 8 / ASP.NET Core. PostgreSQL. Redis cho badge count.

## API docs

Bắt buộc có Swagger khi thêm endpoint REST thật đầu tiên (đọc danh sách/mark-read — xem nguyên
tắc #9 CLAUDE.md root) — dùng `Swashbuckle.AspNetCore`: `AddSwaggerGen()` + `UseSwaggerUI()`
trong `Program.cs`.

## Giao tiếp

- **gRPC expose**: `SendVoIPPush` — gọi bởi Call Service, dùng gRPC (không phải RabbitMQ) vì
  cần độ trễ cực thấp cho cuộc gọi đến (xem `skills/service-communication.md` phần ngoại lệ).
- **RabbitMQ consume**: gần như tất cả — `chat.exchange`, `user.exchange`, `group.exchange`,
  `social.exchange`, `call.exchange`. Riêng `conversation.mute_updated` (từ `chat.exchange`) BẮT
  BUỘC phải consume — đây là cách DUY NHẤT Notification biết trạng thái mute, vì
  `conversation_settings` nằm trong MongoDB của Messaging Service, không được đọc thẳng. Xem
  `docs/.../05-notification-service.md` mục 5.8.
- **RabbitMQ publish** (`notification.exchange`): `notification.new`, `notification.read` —
  để Realtime Gateway push badge/popup in-app.

## Chức năng chính

Push tin nhắn mới (DM/nhóm, tôn trọng `is_muted`) · push @mention (không bị mute ảnh hưởng) ·
push lời mời/kết bạn được chấp nhận · push cuộc gọi đến (VoIP push, KHÔNG thể mute) · push yêu
cầu vào nhóm (admin) · push tương tác bài viết (like/comment/reply/tag) · push tin nhắn chờ từ
người lạ · in-app notification qua WS · quản lý device token · email cho sự kiện quan trọng.

## Lưu ý khi code

- **Cần thêm `MessageReacted` vào enum `NotificationType`** (chưa có ở bản gốc, bổ sung theo
  spec addendum) — consume `message.reacted` để báo "ai đó đã react vào tin nhắn của bạn".
- **Throttle notification react**: nếu 1 message nhận nhiều react liên tiếp trong vài giây, PHẢI
  gộp lại (debounce 5-10s) trước khi push — không bắn 1 push riêng cho mỗi react, sẽ spam người
  dùng.
- Luôn kiểm tra `conversation_mute_state.is_muted` / `mute_until` (bảng riêng của Notification
  Service, đồng bộ qua event `conversation.mute_updated` — KHÔNG phải đọc thẳng
  `conversation_settings` của Messaging Service) và `notification_settings.push_enabled` TRƯỚC
  KHI gửi push, trừ 2 trường hợp không bao giờ bị mute: cuộc gọi đến và @mention.
- Không tự viết logic gọi FCM/APNs — dùng thư viện chính thức (Firebase Admin SDK cho .NET,
  hoặc thư viện APNs .NET có sẵn), không tự implement giao thức push.
