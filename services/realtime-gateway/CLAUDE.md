# CLAUDE.md — Realtime Gateway

> Đọc `/CLAUDE.md` ở root trước. Service này GỘP 2 vai trò cũ: WebSocket Gateway + Presence.

## Vai trò

Quản lý toàn bộ WebSocket connection và trạng thái online/offline/typing. Đây là service duy
nhất client giữ 1 connection lâu dài (không phải request/response ngắn như REST).

## Tech stack

Go. Không có DB quan hệ — toàn bộ state (connection registry, presence) nằm ở Redis.

## API docs

Service này gần như thuần WebSocket, không phải REST — nguyên tắc #9 CLAUDE.md root (mọi
service phải có Swagger) áp dụng CHỈ nếu sau này có thêm REST endpoint thật (không tính WS
handshake). Nếu có, dùng `swaggo/swag` + `gofiber/swagger` như các service Go khác.

## Giao tiếp

- **Verify access_token (JWT) tại chỗ khi WS handshake** — verify chữ ký RS256 bằng public key
  Core Service + check `exp` + Redis GET `cache:jwt_revoked_before`/`cache:jwt_blacklist`, KHÔNG
  gọi gRPC (giống hệt API Gateway, xem `docs/.../05-cookie-auth-flow.md` mục E.8). Không re-verify
  theo TTL 15 phút của JWT giữa phiên WS (sẽ tự ngắt kết nối user đang hoạt động) — thay vào đó
  dựa vào `user.blocked` / event logout toàn bộ ở dòng dưới để chủ động force-disconnect.
- **RabbitMQ publish**: `presence.exchange` — `presence.online`, `presence.offline`,
  `presence.away`, `presence.typing_start`, `presence.typing_stop`.
- **RabbitMQ consume**: gần như tất cả exchange khác (`chat`, `user`, `group`, `notification`,
  `call`, `social`, `media`) — vì vai trò của service này là fan-out MỌI event cần push
  realtime tới đúng client đang mở đúng màn hình. Riêng `user.blocked` / logout-all: dùng để
  force-disconnect ngay các connection của user đó (không đợi JWT hết hạn tự nhiên).

## Chức năng chính

WS handshake + auth · 1 connection / nhiều screen (client gửi `screen_register` khi chuyển màn
hình, không tạo WS mới) · connection registry trong Redis · heartbeat/ping-pong 20s ·
reconnection replay theo `last_event_id` · broadcast theo room · presence
online/offline/away/typing · ẩn trạng thái theo khung giờ (đọc `schedule_hidden_from/to` từ
Core Service qua gRPC) · rate limit WS frame (60 frame/phút/connection).

## Redis key quan trọng (xem đầy đủ ở `skills/naming-conventions.md`)

`cache:ws:user:{user_id}` (Set connID) · `cache:ws:screen:{user_id}:{screen}` ·
`cache:ws:room:{conversation_id}` · `cache:presence:{user_id}` (TTL 35s) ·
`cache:presence:last_seen:{user_id}` (không expire) · `cache:typing:{conv_id}:{user_id}` (TTL 3s).

## Lưu ý khi code — dễ sai nhất

- **Multi-device**: user có thể mở nhiều thiết bị cùng lúc. Chỉ publish `presence.offline` khi
  `cache:ws:user:{id}` (Set connID) RỖNG HOÀN TOÀN — đóng 1 thiết bị không có nghĩa offline nếu
  còn thiết bị khác đang mở.
- Không coi mất kết nối tức thời là offline ngay — dựa vào TTL 35s hết hạn (do heartbeat 20s
  không tới) để quyết định offline, tránh nhấp nháy online/offline khi mạng chập chờn.
- `last_seen_at` không tự push realtime tới member khác trong group — chỉ Core Service lưu bền
  vững, client pull khi cần xem (xem `services/core-service/CLAUDE.md`).
