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
  **CHƯA code** (kênh QR login bên dưới là kênh WS đầu tiên của service này, và nó KHÔNG cần
  JWT — xem mục QR login).
- **RabbitMQ publish**: `presence.exchange` — `presence.online`, `presence.offline`,
  `presence.away`, `presence.typing_start`, `presence.typing_stop`. **CHƯA code.**
- **RabbitMQ consume**: gần như tất cả exchange khác (`chat`, `user`, `group`, `notification`,
  `call`, `social`, `media`) — vì vai trò của service này là fan-out MỌI event cần push
  realtime tới đúng client đang mở đúng màn hình. Riêng `user.blocked` / logout-all: dùng để
  force-disconnect ngay các connection của user đó (không đợi JWT hết hạn tự nhiên). **CHƯA
  code**, ngoại trừ 1 consumer đầu tiên: `user.exchange` / `user.qr_login_approved` (xem mục
  QR login) — phạm vi chỉ đúng binding đó, chưa fan-out tổng quát theo user/room.

## QR login (WS đầu tiên của service này) — xem `docs/.../05-cookie-auth-flow.md` E.11

Cấu trúc package — tách rõ hạ tầng GENERIC (`base/`) khỏi logic RIÊNG từng feature
(`consumer/`, `ws/`):

- `internal/base/socket/` — HOÀN TOÀN GENERIC, không có gì đặt tên riêng theo feature:
  - `registry.go`: `Registry.Register/Unregister/Resolve` (nhận thẳng Redis key/connID/struct
    từ caller).
  - `write.go`: `WriteJSON()`.
  - `heartbeat.go`: `StartHeartbeat(conn)` (ping/pong 20s/60s chuẩn, trả về `stop func()`) +
    `BlockUntilClosed(conn)` — MỌI handler WS của service này gọi 2 hàm này thay vì tự viết lại
    ping loop/pong handler/read loop riêng.
  - `route.go`: `RegisterRoute(app, path, handler)` — đăng ký 1 route WS (middleware
    upgrade-required + route thật) trong 1 lần gọi, `cmd/main.go` không phải lặp lại middleware
    đó cho mỗi route mới.
  Feature mới sau này gọi chung các hàm này, không sửa package này.
- `internal/base/rabbit/` — `DeclareAndConsume(ch, exchange, exchangeType, queue, routingKey)` +
  `Wire(ctx, conn, consumers...)`, GENERIC. `Wire` sở hữu TOÀN BỘ boilerplate lặp lại ở mọi
  consumer (mở channel, declare/bind, vòng lặp goroutine đọc delivery, log) — 1 consumer chỉ cần
  implement interface `rabbit.Consumer` (`Exchange()`/`ExchangeType()`/`Queue()`/`RoutingKey()`/
  `Handle(ctx, delivery)`), KHÔNG tự viết lại vòng lặp/channel riêng (xem `cmd/main.go` gọi
  `rabbit.Wire(ctx, conn, consumer.NewQrLoginConsumer(...), ...)` — thêm consumer mới chỉ cần
  thêm 1 dòng vào danh sách đó).
- `internal/base/message/{feature}/` — struct thật sự bắn xuống WS, tên LUÔN kết thúc bằng
  `Publish` (VD `qrlogin.ApprovedPublish`, `qrlogin.ExpiredPublish` — mirror đúng convention
  suffix DTO của Java, Go dùng `Publish` cho "struct bắn ra qua WS"), + `RedisKey()`/`TTL` riêng
  của feature đó. Mỗi struct tự có field `Type` riêng, KHÔNG bọc qua 1 envelope chung — với đúng
  1-2 struct/feature thì 1 lớp wrapper chỉ thêm nesting không cần thiết.
- `internal/consumer/{feature}_consumer.go` — 1 struct/consumer (VD `QrLoginConsumer`),
  implement `rabbit.Consumer`, tự biết exchange/routing key/queue/payload/dedup của riêng nó,
  `Handle()` gọi vào `base/socket` để resolve connection + push. Feature mới = thêm 1 file mới ở
  đây + 1 dòng trong danh sách truyền cho `rabbit.Wire(...)` ở `cmd/main.go`, không sửa file cũ.
- `internal/ws/{feature}_handler.go` — WS handshake handler riêng từng feature (VD
  `qr_login_handler.go`: `GET /ws/qr-login?token=...`, KHÔNG verify JWT — kênh này dành cho
  thiết bị CHƯA đăng nhập, chấp nhận connect ngay, tự hết hạn theo timer `qrlogin.TTL` nếu không
  có event khớp tới), gọi vào `base/socket` để đăng ký/ghi.

Dedup theo `event_id` qua Redis SETNX `cache:received_event_dedup:{event_id}` (thay cho bảng SQL
`received_event_dedup` vì service này không có DB quan hệ). Heartbeat Ping/Pong 20s đã code theo
đúng mục "Chức năng chính" bên dưới.

Phần đã code này CHỈ đủ cho QR login (1 connection/1 key, không multiplex nhiều topic/1
connection) — chưa phải hạ tầng fan-out tổng quát (per-user registry dạng Set/SADD, room
broadcast, subscribe/unsubscribe nhiều topic/1 connection, multi-instance Pub/Sub routing) mô tả
ở "Chức năng chính"/"Redis key quan trọng" bên dưới, những phần đó vẫn CHƯA code — nhưng
`base/socket`/`base/rabbit` đã thiết kế generic từ đầu nên khi làm tới phần đó không cần sửa lại
2 package này, chỉ cần thêm `message/<feature>/` + `consumer/<feature>_consumer.go` +
`ws/<feature>_handler.go` mới.

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
