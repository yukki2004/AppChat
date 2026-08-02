# CLAUDE.md — API Gateway

> Đọc `/CLAUDE.md` ở root trước. File này chỉ orientation riêng cho service này.

## Vai trò

Entry point duy nhất cho mọi HTTP REST request từ client. Không xử lý WebSocket (đó là
Realtime Gateway). Không có business logic — chỉ routing, auth, rate limit, circuit breaker.

## Tech stack

Go (Fiber framework). Stateless — không có DB riêng, chỉ dùng Redis cho cache session verify
và rate-limit counter.

## Giao tiếp

- **Verify access_token (JWT) tại chỗ** — KHÔNG gọi gRPC mỗi request. Verify chữ ký RS256 bằng
  public key của Core Service + check `exp`, sau đó Redis GET `cache:jwt_revoked_before:{sub}`
  và `cache:jwt_blacklist:{jti}` để bắt các trường hợp bị revoke sớm (logout/block). Chi tiết:
  `docs/.../05-cookie-auth-flow.md`.
- **gRPC gọi ra**: `core-service.RefreshAccessToken(refresh_token)` — chỉ gọi khi route
  `/auth/refresh`, không phải mọi request.
- **Forward HTTP** tới service đích sau khi verify xong, kèm header `X-User-Id`.
- **Client IP tin cậy**: đọc `X-Forwarded-For`/`CF-Connecting-IP` rồi ghi đè thành `X-Client-IP`
  khi forward xuống downstream — luôn strip giá trị client tự gửi lên trước, không bao giờ
  forward thẳng. Đây là nguồn IP duy nhất Core Service dùng để ghi `user_sessions.ip_address` và
  resolve geo-location (xem `docs/.../05-cookie-auth-flow.md` E.10).
- Không publish/consume RabbitMQ trực tiếp.

## Chức năng chính

Routing/reverse proxy · cookie authentication (JWT `access_token`, KHÔNG dùng Authorization
header, KHÔNG phải `session_id` — xem nguyên tắc #4 CLAUDE.md root) · rate limiting (token
bucket per IP + per user) · request ID injection (UUID v4 vào `X-Request-ID`) · CORS · SSL
termination · circuit breaker per-downstream · response compression · health check (`/health`,
`/health/live`, `/health/ready`).

**Đã code**: reverse proxy (`internal/proxy`), verify JWT + check revoke (`internal/middleware`
`CookieAuth`), `X-Client-IP` injection, request ID, CORS, health check. Route table hiện chỉ có
các route của Core Service auth/2FA đã có (`cmd/main.go`) — thêm route mới cho service khác thì
thêm vào route table đó theo đúng phân loại public/protected.

**Chưa code** (biết trước, không phải quên): rate limiting, circuit breaker, timeout cứng 30s,
SSL termination, response compression, `/auth/refresh` (chưa có gRPC `RefreshAccessToken` phía
Core Service để gọi).

## Lưu ý khi code

- Đừng nhầm "verify JWT tại chỗ" với "không cần check gì thêm" — vẫn PHẢI Redis GET 2 key revoke
  ở mục Giao tiếp cho MỌI request, nếu không sẽ không đáp ứng được yêu cầu block/logout có hiệu
  lực tức thời (JWT tự bản chất không revoke được giữa chừng).
- Route trả 401 khi JWT hết hạn phải dùng đúng `error.code: "TOKEN_EXPIRED"` trong envelope
  `{success, data, error}` (khớp `ApiResponse` bên Core Service, không phải field `error_code`
  phẳng kiểu cũ) — client dựa vào code này để biết nên gọi `/auth/refresh` hay bắt đăng nhập lại.
- Timeout cứng 30s mỗi upstream call, trả 504 nếu vượt — không để request treo vô hạn. (Chưa
  code — xem mục "Chưa code" ở trên.)
- Route `/auth/register`, `/auth/login`, `/auth/login/2fa/challenge`, `/auth/login/2fa` không
  cần verify JWT trước khi forward (đây chính là luồng TẠO ra access_token, chưa có gì để
  verify) — các route còn lại mặc định coi là cần auth trừ khi liệt kê rõ là public trong route
  table.
