# **PHỤ LỤC E – COOKIE-BASED AUTH FLOW (ACCESS + REFRESH TOKEN)**

## **E.0 Tổng quan cơ chế**

Hệ thống dùng 2 token, tách vai trò rõ ràng, cả 2 đều truyền qua cookie (không phải header
`Authorization`, không phải body):

| Token | Loại | TTL | Lưu ở đâu | Ai verify |
|---|---|---|---|---|
| `pre_auth_token` | Chuỗi UUID v4 ngẫu nhiên | 5 phút | Redis only (không lưu DB) | Chỉ Core Service, dùng riêng cho bước nộp mã 2FA — xem E.9 |
| `access_token` | JWT (RS256) | 15 phút | Chỉ trong cookie, không lưu server | API Gateway, WS Gateway tự verify chữ ký tại chỗ |
| `refresh_token` | Chuỗi UUID v4 ngẫu nhiên, không mã hoá thông tin | 30 ngày (rolling) | Redis + `user_sessions` (PostgreSQL) — DB chỉ lưu `SHA-256(refresh_token)`, không lưu giá trị gốc | Chỉ Core Service, qua gRPC `RefreshAccessToken` |

Lý do tách 2 token: `access_token` JWT giúp Gateway verify **không cần gRPC mỗi request**
(giảm tải Core Service, giảm latency), nhưng JWT tự chứa claim nên không thể revoke tức thời
theo bản chất — vì vậy vẫn giữ `refresh_token` kiểu cũ (random, tra Redis) làm nguồn sự thật để
revoke ngay khi logout/block, và bù lại độ trễ revoke của access token bằng cơ chế ở mục E.6.

## **E.1 Cấu hình Cookie**

| | `access_token` | `refresh_token` |
|---|---|---|
| **HttpOnly** | true | true |
| **Secure** | true | true |
| **SameSite** | Strict | Strict |
| **Path** | `/` | `/auth/refresh` (chỉ gửi lên đúng endpoint refresh, giảm bề mặt lộ token) |
| **Domain** | `.chatapp.com` | `.chatapp.com` |
| **Max-Age** | 900 (15 phút) | 2592000 (30 ngày) |
| **Giá trị** | JWT (`header.payload.signature`) | UUID v4 ngẫu nhiên |

## **E.2 JWT `access_token` — cấu trúc claim**

```json
{
  "sub": "user_id (UUID v4)",
  "jti": "UUID v4 – định danh riêng của lần phát hành token này",
  "iat": 1753500000,
  "exp": 1753500900
}
```

- Ký bằng **RS256**, private key CHỈ Core Service giữ (không service nào khác được phát hành
  token). Public key phân phối qua endpoint `GET /.well-known/jwks.json` (Core Service expose,
  public, không cần auth) để API Gateway/WS Gateway/service khác tự verify chữ ký tại chỗ —
  không cần gọi ngược gRPC về Core Service để biết token có hợp lệ hay không. Chi tiết cách
  cache JWKS, xoay vòng key (`kid`), thư viện dùng theo từng ngôn ngữ: `skills/authentication.md`.
- Không nhét thêm claim nghiệp vụ hay đổi (role, display_name...) vào JWT — access token chỉ
  dùng để xác định `user_id`, mọi dữ liệu khác vẫn lấy qua gRPC/cache như cũ
  (`GetUserPublicInfo`...). Tránh vấn đề "đổi role xong nhưng JWT cũ vẫn còn quyền cũ tới khi
  hết hạn".

## **E.3 Đăng nhập – Set Cookie**

**Trường hợp KHÔNG bật 2FA:**

```
POST /auth/login  { email, password }

→ Core Service verify password
→ tạo refresh_token (UUID v4), lưu Redis + user_sessions (chỉ lưu SHA-256(refresh_token))
→ tạo access_token (JWT, ký RS256, exp = now + 15p, jti mới)

← HTTP 200
  Set-Cookie: access_token=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=900
  Set-Cookie: refresh_token=<uuid>; HttpOnly; Secure; SameSite=Strict; Path=/auth/refresh; Max-Age=2592000
```

**Trường hợp CÓ bật 2FA — xem đầy đủ ở E.9. Ở bước này TUYỆT ĐỐI KHÔNG phát access_token/
refresh_token thật** — chỉ trả về `pre_auth_token` để client dùng cho bước nộp mã 2FA tiếp theo.

## **E.4 Request có xác thực (đường đi thường xuyên nhất)**

```
GET /api/messages?conv_id=abc
  Cookie: access_token=<jwt>   ← browser tự đính kèm

→ API Gateway:
   1. Verify chữ ký JWT bằng public key (tại chỗ, KHÔNG gọi gRPC)
   2. Check exp chưa hết hạn
   3. Redis GET cache:jwt_revoked_before:{sub} và cache:jwt_blacklist:{jti}
      (xem E.6) — nếu bị revoke, trả 401 TOKEN_REVOKED
   4. Forward request với header X-User-Id: {sub}

→ Target service đọc X-User-Id (không verify lại, như cũ)
```

Không còn gRPC `VerifySession` gọi mỗi request — đây là điểm khác biệt chính so với thiết kế
session_id thuần tuý trước đây. gRPC vào Core Service giờ chỉ cần khi **refresh token** (mục
E.5) hoặc các nghiệp vụ khác vốn đã dùng gRPC (`CheckFriendship`, `CheckBlock`...).

## **E.5 Access token hết hạn – Refresh**

Access token sống ngắn (15 phút) nên hết hạn thường xuyên — client (app/web) chủ động gọi refresh
khi gặp lỗi hết hạn, KHÔNG phải Gateway tự refresh hộ (tránh Gateway phải giữ state/logic retry
phức tạp):

```
Request bất kỳ → 401 { error_code: "TOKEN_EXPIRED" }

→ Client gọi: POST /auth/refresh
  Cookie: refresh_token=<uuid>   ← browser tự đính kèm (Path=/auth/refresh)

→ API Gateway forward thẳng (không verify access_token) sang Core Service
→ Core Service: gRPC nội bộ RefreshAccessToken(refresh_token)
   1. Tra refresh_token trong Redis/DB — không tồn tại hoặc revoked_at != NULL → 401, buộc
      đăng nhập lại
   2. Còn hợp lệ → phát access_token MỚI (jti mới) + xoay vòng refresh_token MỚI (rotation):
      vô hiệu hoá refresh_token cũ, tạo refresh_token mới cùng expires_at (không reset lại 30
      ngày từ đầu, giữ nguyên hạn gốc trừ khi hoạt động liên tục thì rolling như cũ)
   3. Nếu refresh_token cũ bị dùng lại lần 2 (dấu hiệu bị đánh cắp) → revoke TOÀN BỘ session của
      user, buộc đăng nhập lại mọi thiết bị (reuse detection)

← Set-Cookie access_token mới + refresh_token mới
→ Client tự động retry request gốc
```

## **E.6 Revoke tức thời — bù đắp hạn chế "JWT không revoke được giữa chừng"**

Đây là phần quan trọng nhất khi đổi sang JWT: access token tự chứa claim, về bản chất KHÔNG thể
xoá khỏi lưu hành trước khi hết hạn tự nhiên. Hệ thống bù lại bằng 2 cơ chế Redis, Gateway phải
check ở bước 3 của mục E.4 cho MỌI request:

| Tình huống | Hành động | Redis key | Gateway kiểm tra thế nào |
|---|---|---|---|
| Đăng xuất 1 thiết bị | Revoke đúng 1 `refresh_token` (DB `revoked_at`) + blacklist đúng access token đang dùng | `cache:jwt_blacklist:{jti}` = 1, TTL = thời gian còn lại tới `exp` của token đó | Nếu `jti` trong JWT có trong blacklist → 401 |
| Đăng xuất tất cả thiết bị / đổi mật khẩu / Admin block | Revoke TẤT CẢ `refresh_token` của user + đánh dấu mốc revoke toàn cục | `cache:jwt_revoked_before:{user_id}` = now(), TTL = 900s (bằng đúng TTL tối đa của access token — sau 15 phút mọi JWT cũ chắc chắn tự hết hạn nên key hết cần tồn tại) | Nếu JWT có `iat` <= giá trị key này → 401 |

Nhờ TTL của 2 key trên luôn khớp với thời điểm access token tự nhiên hết hạn, Redis tự dọn key,
không cần job cleanup riêng.

- **Block tài khoản (Admin)**: `PATCH /admin/users/{id}/block` → Core Service set
  `users.is_blocked=true` → revoke tất cả `refresh_token` trong Redis/DB → set
  `cache:jwt_revoked_before:{user_id}` → publish `user.blocked` → WS Gateway force-disconnect.
  Request tiếp theo của user bị chặn ngay ở bước 3 (E.4), không cần đợi JWT hết hạn tự nhiên.

## **E.7 Đăng xuất – Clear Cookie**

```
POST /auth/logout
→ Core Service: revoke refresh_token hiện tại (revoked_at) + blacklist jti access_token hiện tại
← HTTP 200
  Set-Cookie: access_token=; Max-Age=0
  Set-Cookie: refresh_token=; Max-Age=0
```

## **E.8 WebSocket — khác biệt so với REST**

WS là kết nối dài, `access_token` 15 phút sẽ hết hạn giữa chừng phiên kết nối:

- **Handshake**: WS Gateway verify JWT y hệt bước 1-3 của E.4 (tại chỗ, không gRPC).
- **Giữa phiên**: không re-verify JWT liên tục theo TTL của nó (sẽ tự ngắt kết nối user đang
  hoạt động sau 15 phút, trải nghiệm tệ). Thay vào đó: WS Gateway chỉ cần **subscribe** vào cùng
  cơ chế Redis key ở mục E.6 (`jwt_revoked_before` / `jwt_blacklist`) qua RabbitMQ event
  `user.blocked` / `user.logged_out_all` để chủ động force-disconnect ngay khi có revoke — không
  dựa vào việc JWT hết hạn để phát hiện.
- Client vẫn phải gọi `/auth/refresh` định kỳ (khi access token gần hết hạn) qua kênh REST bình
  thường, KHÔNG refresh qua WS — WS Gateway không cấp token.

## **E.9 Đăng nhập khi có bật 2FA (TOTP/SMS/Email) — `pre_auth_token`**

Vấn đề cần tránh: nếu phát access_token/refresh_token thật ngay sau khi verify password xong
(trước khi verify mã 2FA), toàn bộ mục đích của 2FA bị vô hiệu hoá — chỉ cần đúng password là
coi như đăng nhập xong, mã 2FA chỉ là bước UI thừa. Vì vậy bắt buộc có 1 trạng thái trung gian:

```
POST /auth/login  { email, password }

→ Core Service verify password đúng
→ Kiểm tra two_factor_auth.is_enabled = true
→ KHÔNG tạo access_token/refresh_token ở bước này
→ Tạo pre_auth_token (UUID v4), lưu Redis:
  cache:pre_auth:{pre_auth_token} = { user_id }, TTL 5 phút, KHÔNG lưu DB (sống quá ngắn, không
  cần bền vững)

← HTTP 200 { requires_2fa: true, pre_auth_token: "<uuid>", method: "TOTP" }
  (KHÔNG Set-Cookie access_token/refresh_token ở response này)
```

```
POST /auth/login/2fa  { pre_auth_token, code }

→ Core Service: Redis GET cache:pre_auth:{pre_auth_token} — không tồn tại/hết hạn → 401,
  bắt đăng nhập lại từ đầu (không cho thử lại vô hạn với cùng 1 pre_auth_token)
→ Verify code (TOTP/OTP/backup code) đúng cách như trước
→ Redis DEL cache:pre_auth:{pre_auth_token} ngay (dùng 1 lần, không tái sử dụng)
→ Tạo refresh_token + access_token THẬT như luồng bình thường (mục E.3)

← HTTP 200 + Set-Cookie access_token + refresh_token
```

**Quy tắc cứng:**
- `pre_auth_token` chỉ dùng được đúng 1 lần, cho đúng 1 mục đích (nộp mã 2FA) — không phải
  access token rút gọn, không mang bất kỳ quyền truy cập nghiệp vụ nào khác.
- Sai mã 2FA quá số lần cho phép (dùng lại `max_attempts` kiểu như `otp_codes`) → xoá luôn
  `pre_auth_token`, bắt đăng nhập lại từ bước nhập password.
- Endpoint `/auth/login/2fa` không đọc `X-User-Id` hay bất kỳ cookie auth nào — toàn bộ danh
  tính lấy từ `pre_auth_token`, vì tại thời điểm này user CHƯA được coi là đã đăng nhập.

*─── Hết tài liệu ───*
