# CLAUDE.md — Messaging Service

> Đọc `/CLAUDE.md` ở root trước. Đây là service quan trọng nhất về mặt sản phẩm — write
> volume cao nhất hệ thống.

## Vai trò

Chat 1-1 và nhóm: gửi/nhận, seen/delivered, unsend, pin, pending message (người lạ), thread
reply, poll/vote, tin nhắn tạm thời.

## Tech stack

Java 25 / Spring Boot 4. MongoDB (`messages`, `conversations`) + PostgreSQL cho phần liên quan
tới metadata do Group cũ quản lý trước đây (nay đã gộp vào Core, Messaging chỉ gRPC sang lấy).

## Giao tiếp

- **gRPC gọi ra**: `core-service.CheckFriendship` / `CheckBlock` (chat 1-1),
  `core-service.GetGroupMembers` / `CheckGroupRole` (chat nhóm — validate còn trong nhóm
  trước khi cho gửi).
- **gRPC expose**: `CreateConversation` (Core Service gọi lúc tạo nhóm mới — đồng bộ, xem
  `docs/.../04-messaging-service.md` mục 4.11a), `GetConversation`, `GetMessages`,
  `GetPinnedMessages`.
- **RabbitMQ publish** (`chat.exchange`): `message.sent`, `message.delivered`, `message.seen`,
  `message.deleted`, `message.edited`, `message.reacted`, `message.pinned`, `message.mention`,
  `message.thread_replied`, `message.poll_voted`, `pending.received`, `pending.accepted`,
  `conversation.mute_updated` (Notification Service consume để tự cập nhật bản sao mute state,
  xem `docs/.../04-messaging-service.md` mục 4.10 — Notification KHÔNG được đọc thẳng
  `conversation_settings` trong MongoDB của Messaging).
- **RabbitMQ consume**: `media.upload_completed` (cập nhật `media_url` vào message đã tạo sẵn),
  `group.member_joined` / `group.member_removed` / `group.deleted` (đồng bộ
  `conversations.participant_ids` — xem mục 4.11b, BẮT BUỘC không được bỏ qua).

## Chức năng chính

Gửi text/ảnh/video/file/GIF/sticker · trạng thái sent/delivered/seen · thu hồi (1 phía / 2
phía) · forward · reply · react emoji · ghim tin nhắn · search full-text · pending message
(người lạ chưa kết bạn) · **thread reply (1 cấp, không đệ quy)** · **poll/vote** · **tin nhắn
tạm thời (ephemeral)**.

## Lưu ý khi code — race condition & ràng buộc đã phát hiện lúc review spec

1. **Thứ tự bắt buộc khi gửi kèm file nặng**: `POST /messages` (tạo message
   `status=UPLOADING`, `media_url=null`) PHẢI hoàn tất và có `message_id` trước khi client gọi
   `media/upload/init` với `context_id=message_id`. Không được để 2 request này chạy song song
   ngay từ đầu — nếu không, `media.upload_completed` có thể tới trước khi message tồn tại.
2. **Thread reply chống đệ quy**: khi tạo reply, nếu message target đã có `thread_root_id` →
   reply mới LUÔN gán vào root đó (không tạo thread lồng thêm). Ràng buộc này nằm ở tầng
   service khi insert, không phải validate ở UI client.
3. **Poll — không dùng UNIQUE constraint cứng cho vote**: luôn để
   `UNIQUE(message_id, option_index, user_id)` trong `poll_votes`. Khi `poll_allow_multiple =
   false`, tầng service phải tự xoá vote cũ của user trước khi insert vote mới — không đặt
   ràng buộc DB kiểu "chỉ 1 vote/user" vì nó mâu thuẫn với trường hợp cho phép chọn nhiều.
4. **Ephemeral message TTL**: `expires_at` dùng MongoDB TTL index để tự xoá. Vấn đề CHƯA giải
   quyết trong schema: client đang mở màn hình không tự biết message đã bị TTL xoá (Mongo
   không tự bắn event). Cần thêm 1 trong 2: (a) worker riêng dùng MongoDB Change Stream lắng
   nghe delete → publish `message.expired` → WS push xoá bubble; (b) chấp nhận UX "im lặng biến
   mất, chỉ đúng khi F5". Phải chốt hướng nào trước khi code phần này.
5. **Idempotency**: `message_id` là UUID v4 client-generated — dùng để chống tạo trùng khi
   client retry do timeout, KHÔNG dùng `_id` MongoDB tự sinh cho việc này.
6. **Nhóm broadcast (`groups.only_admin_can_send=true`)**: trước khi cho Member gửi tin trong
   chat nhóm, ngoài `CheckGroupRole` để xác nhận còn trong nhóm, PHẢI đọc thêm field này (qua
   `GetGroupInfo`) — nếu true và role là MEMBER (không phải ADMIN/OWNER) → reject, trả lỗi rõ
   ràng (VD `GROUP_BROADCAST_ONLY`), không phải lỗi chung chung "not in group".
7. **Đổi `is_muted`/`mute_until` PHẢI publish `conversation.mute_updated`** cùng transaction/
   outbox — không chỉ update `conversation_settings` rồi thôi. Notification Service phụ thuộc
   hoàn toàn vào event này để biết có nên push hay không (nó không đọc thẳng MongoDB của mình).
   Quên bước publish này = Notification Service push nhầm cho người đã mute.
8. **Sửa tin nhắn (edit)**: PHẢI đẩy nội dung CŨ vào `edit_history` TRƯỚC KHI ghi đè `content` —
   không được ghi đè trực tiếp rồi thôi, mất luôn bằng chứng nếu sau này tin nhắn đó bị report
   (mục 3.16 Core Service). Chỉ cho sửa `MESSAGE_TEXT`, trong 15 phút kể từ `sent_at`, không cho
   sửa nếu đã bị thu hồi (`deleted_for_all`/`deleted_for_sender`). Nếu nội dung mới thêm
   @mention chưa từng có ở bản cũ → publish `message.mention` CHỈ cho user mới, so sánh
   `mentions` cũ và mới trước khi publish để tránh gửi trùng thông báo cho người đã được tag
   từ trước.
9. **Tạo conversation DIRECT PHẢI dùng `findOneAndUpdate upsert` trên `direct_pair_key`**, không
   được "check tồn tại rồi mới insert" (2 bước tách rời vẫn bị race condition nếu 2 request tới
   cùng lúc). Xem mục 4.11c.
10. **Tin nhắn "người lạ" (pending) vẫn có `conversation_id` hợp lệ ngay từ đầu** —
    conversation được tạo NGAY lúc gửi (không đợi accept), chỉ khác là `is_pending_request=true`
    nên KHÔNG hiện trong danh sách chat chính của receiver cho tới khi họ Accept. Đừng để
    `conversation_id` null hay tạo conversation muộn hơn lúc gửi tin — xem mục 4.11d.
11. **Kick/rời/xoá nhóm PHẢI cập nhật `conversations.participant_ids`** ngay khi consume
    `group.member_joined`/`group.member_removed`/`group.deleted` — không bỏ qua các event này
    dù chúng không trực tiếp liên quan tới nội dung tin nhắn, vì đây là nguồn duy nhất giữ
    `participant_ids` khớp với `group_members` thật bên Core Service. Xem mục 4.11b.
