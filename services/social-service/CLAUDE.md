# CLAUDE.md — Social Service

> Đọc `/CLAUDE.md` ở root trước.

## Vai trò

Story, post, react, comment, feed, tag — phần "mạng xã hội" tách biệt hoàn toàn khỏi chat.

## Tech stack

Java 25 / Spring Boot 4. PostgreSQL (post, comment, reaction) + MongoDB (story view, feed
cache).

## API docs

Bắt buộc có Swagger khi thêm endpoint REST thật đầu tiên (xem nguyên tắc #9 CLAUDE.md root) —
dùng `springdoc-openapi-starter-webmvc-ui` giống core-service, chỉ cần thêm dependency vào
`pom.xml`, không cần config thêm.

## Giao tiếp

- **gRPC gọi ra**: `core-service.CheckFriendship` / `CheckBlock` — lọc nội dung theo quan hệ
  bạn bè trước khi trả feed/profile.
- **RabbitMQ publish** (`social.exchange`): `social.post_created`, `social.post_reacted`,
  `social.post_commented`, `social.comment_replied`, `social.tag`, `social.story_created`.
- **RabbitMQ consume**: `media.upload_completed` (cập nhật `media_urls` của post sau khi ảnh/
  video upload xong) · `user.profile_updated` (cập nhật hiển thị tên/avatar trên story/post cũ
  — xem nguyên tắc snapshot ở `services/core-service/CLAUDE.md`).

## Chức năng chính

Đăng story (ảnh/video/text, hết hạn 24h) · xem story theo privacy · react story → tạo message
reply bên Messaging Service · viewers list · ghim story (highlight) · đăng/sửa/xoá bài viết ·
react bài viết · bình luận (nested reply) · chia sẻ bài viết · báo cáo vi phạm · tag bạn bè ·
feed bạn bè theo `created_at`, pagination cursor.

## Lưu ý khi code

- Story hết hạn 24h dùng `expires_at` — cân nhắc cùng cơ chế TTL/cron như ephemeral message bên
  Messaging Service, nhưng KHÔNG dùng chung code/collection, đây là domain độc lập.
- `content_reports` (báo cáo vi phạm) nên thiết kế đủ tổng quát để dùng chung được cho
  `content_type=message` sau này (Messaging Service sẽ cần báo cáo tin nhắn) — không thiết kế
  chỉ riêng cho post/comment/story rồi phải tạo bảng mới sau.
- Reaction/comment count trên `posts` là denormalized counter — PHẢI update bằng atomic
  increment (`UPDATE ... SET count = count + 1`), không đọc-sửa-ghi, để tránh lệch số dưới tải
  cao.
- Không JOIN thẳng bảng `users` (thuộc Core Service) — mọi thông tin hiển thị tên/avatar lấy
  qua gRPC `GetUserPublicInfo` hoặc cache đã đồng bộ qua `user.profile_updated`.
