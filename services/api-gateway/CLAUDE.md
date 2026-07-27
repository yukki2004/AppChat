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
- Không publish/consume RabbitMQ trực tiếp.

## Chức năng chính

Routing/reverse proxy · cookie authentication (đọc `session_id`, KHÔNG dùng Authorization
header/JWT) · rate limiting (token bucket per IP + per user) · request ID injection (UUID v4
vào `X-Request-ID`) · CORS · SSL termination · circuit breaker per-downstream · response
compression · health check (`/health`, `/health/live`, `/health/ready`).

## Lưu ý khi code

- Đừng nhầm "verify JWT tại chỗ" với "không cần check gì thêm" — vẫn PHẢI Redis GET 2 key revoke
  ở mục Giao tiếp cho MỌI request, nếu không sẽ không đáp ứng được yêu cầu block/logout có hiệu
  lực tức thời (JWT tự bản chất không revoke được giữa chừng).
- Route trả 401 khi JWT hết hạn phải dùng đúng `error_code: "TOKEN_EXPIRED"` (không phải
  `TOKEN_REVOKED` hay lỗi 401 chung chung) — client dựa vào code này để biết nên gọi
  `/auth/refresh` hay bắt đăng nhập lại.
- Timeout cứng 30s mỗi upstream call, trả 504 nếu vượt — không để request treo vô hạn.
- Route `/auth/login`, `/auth/register`, OAuth callback không cần VerifySession trước khi
  forward (chưa có session).
