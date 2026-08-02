# **PHỤ LỤC E – COOKIE-BASED AUTH FLOW (ACCESS + REFRESH TOKEN)**

## **E.0 Tổng quan cơ chế**

Hệ thống dùng 3 token, tách vai trò rõ ràng, CẢ 3 đều truyền qua cookie (không phải header
`Authorization`, không phải body) — nhất quán 1 cơ chế xuyên suốt, không trộn "chỗ này cookie,
chỗ kia trả token qua body":

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

| | `access_token` | `refresh_token` | `pre_auth_token` |
|---|---|---|---|
| **HttpOnly** | true | true | true |
| **Secure** | true | true | true |
| **SameSite** | Strict | Strict | Strict |
| **Path** | `/` | `/auth` (không phải `/auth/refresh` — cookie path match theo TIỀN TỐ, RFC 6265, và `/auth/refresh` KHÔNG phải tiền tố của `/auth/logout`/`/auth/logout-all` nên browser sẽ không gửi cookie lên 2 endpoint đó nếu để path hẹp vậy; `/auth` là tiền tố hẹp nhất bao được cả `/auth/refresh`, `/auth/logout`, `/auth/logout-all`) | `/auth/login/2fa` (match cả `/auth/login/2fa` và `/auth/login/2fa/challenge` — cookie path match theo prefix, RFC 6265) |
| **Domain** | `.chatapp.com` | `.chatapp.com` | `.chatapp.com` |
| **Max-Age** | 900 (15 phút) | 2592000 (30 ngày) | 300 (5 phút, khớp TTL Redis) |
| **Giá trị** | JWT (`header.payload.signature`) | UUID v4 ngẫu nhiên | UUID v4 ngẫu nhiên |

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
  Set-Cookie: refresh_token=<uuid>; HttpOnly; Secure; SameSite=Strict; Path=/auth; Max-Age=2592000
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
  Cookie: refresh_token=<uuid>   ← browser tự đính kèm (Path=/auth)

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

3 biến thể, cùng cơ chế revoke ở E.6 nhưng khác phạm vi:

**Đăng xuất thiết bị hiện tại:**

```
POST /auth/logout
→ Core Service: revoke refresh_token hiện tại (revoked_at) + blacklist jti access_token hiện tại
  (đọc jti trực tiếp từ access_token cookie của chính request này — biết chính xác, blacklist
  tức thời)
← HTTP 200
  Set-Cookie: access_token=; Path=/; Max-Age=0
  Set-Cookie: refresh_token=; Path=/auth; Max-Age=0
```

**Đăng xuất 1 thiết bị khác (màn quản lý thiết bị đăng nhập):**

```
DELETE /auth/sessions/{sessionId}
  Cookie: access_token=<jwt>   ← của THIẾT BỊ HIỆN TẠI, chỉ để xác thực danh tính người gọi

→ Core Service: check sessionId thuộc đúng user gọi, revoke refresh_token của session đó
← HTTP 200 (không Set-Cookie gì — không đụng tới cookie của thiết bị đang gọi request này)
```

Không blacklist được `jti` của thiết bị bị đăng xuất — Core Service chỉ lưu `token_hash` của
`refresh_token` trong `user_sessions`, không lưu `jti` theo từng thiết bị (tránh phải ghi DB mỗi
lần refresh chỉ để phục vụ đúng 1 tình huống hiếm này). Nên access_token của thiết bị đó vẫn còn
hiệu lực tới tối đa 15 phút — chấp nhận được vì refresh_token đã revoke nên sau đó chắc chắn
không refresh lại được nữa.

**Đăng xuất tất cả thiết bị:**

```
POST /auth/logout-all?keep_current=false   (mặc định false)

→ Core Service: revoke TẤT CẢ refresh_token của user
→ keep_current=false: set thêm cache:jwt_revoked_before:{user_id} (E.6) → chặn NGAY mọi
  access_token đang lưu hành, kể cả thiết bị đang gọi request này
→ keep_current=true: refresh_token của thiết bị hiện tại KHÔNG bị revoke, access_token hiện tại
  giữ nguyên hiệu lực — các thiết bị khác rơi vào tình huống như DELETE /auth/sessions/{id} ở
  trên (không blacklist tức thời, chờ hết hạn tự nhiên ≤15 phút)
← HTTP 200
  keep_current=false: Set-Cookie access_token=;Path=/;Max-Age=0 + refresh_token=;Path=/auth;Max-Age=0
  keep_current=true: không Set-Cookie gì (thiết bị hiện tại vẫn đăng nhập)
```

WS Gateway cần được báo để force-disconnect socket đang mở khi `logout-all` — xem E.8
(`user.logged_out_all`). **Hiện CHƯA publish thật** vì outbox pattern (bảng `outbox_events` +
relay worker) chưa được wire ở Core Service — đã để `TODO` tại call site, chờ hạ tầng outbox.

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

3 bước — KHÔNG tự ý chọn method hộ user và KHÔNG gửi OTP cho tới khi user chủ động chọn (tự
động chọn "method mạnh nhất" rồi gửi SMS/email ngay lúc login là sai: user có thể đang không
cầm điện thoại cài TOTP, muốn dùng ngay method khác — tự gửi trước khi hỏi vừa tốn phí SMS/email
vô ích vừa sai ý user; đây là cách Google/GitHub thực tế đang làm — hỏi chọn trước, gửi mã sau):

**Bước 1 — verify password, biết CÓ bật 2FA hay không, CHƯA gửi mã nào:**

```
POST /auth/login  { identifier, password }

→ Core Service verify password đúng
→ Đọc two_factor_methods WHERE user_id = ? — có ít nhất 1 dòng ⇒ đã bật 2FA (multi-method, xem
  `03-core-service.md` mục two_factor_methods)
→ KHÔNG tạo access_token/refresh_token ở bước này
→ KHÔNG chọn method hộ, KHÔNG dispatch challenge, KHÔNG gửi OTP nào ở bước này
→ Tạo pre_auth_token (UUID v4), lưu Redis:
  cache:pre_auth:{pre_auth_token} = { user_id }, TTL 5 phút, KHÔNG lưu DB (sống quá ngắn, không
  cần bền vững)

← HTTP 200 { requires_2fa: true, available_methods: ["TOTP", "EMAIL"] }   (sắp xếp theo độ mạnh
  TOTP > EMAIL > SMS để client gợi ý mặc định, nhưng KHÔNG tự chọn hộ)
  Set-Cookie: pre_auth_token=<uuid>; HttpOnly; Secure; SameSite=Strict; Path=/auth/login/2fa;
  Max-Age=300   (KHÔNG Set-Cookie access_token/refresh_token ở response này, và KHÔNG trả
  pre_auth_token trong body — cùng 1 cơ chế cookie như access/refresh_token, xem E.0/E.1)
```

**Bước 2 — user chọn method, Core Service mới dispatch/gửi mã cho đúng method đó:**

```
POST /auth/login/2fa/challenge  { method }
  Cookie: pre_auth_token=<uuid>   ← browser tự đính kèm (Path=/auth/login/2fa)

→ Core Service: Redis GET cache:pre_auth:{pre_auth_token} — không tồn tại/hết hạn → 401 (thiếu
  cookie thì coi như không có, không xử lý gì thêm, trả lỗi thẳng)
→ Verify `method` nằm trong danh sách method đã bật của user (không cho challenge method chưa
  từng bật)
→ Dispatch challenge theo đúng method: TOTP không gửi gì (user tự mở app); SMS/EMAIL sinh OTP,
  INSERT otp_codes (purpose=LOGIN_2FA), gửi SMS/email
→ Redis SET lại cache:pre_auth:{pre_auth_token} = { user_id, method } (GIỮ NGUYÊN pre_auth_token
  và TTL còn lại — không cấp token mới, không set lại cookie). Gọi lại endpoint này với method
  khác = đổi sang method đó (dùng chung 1 endpoint cho cả lần chọn đầu tiên lẫn "thử cách khác")

← HTTP 200 { method: "EMAIL", message: "Code sent" }
```

**Bước 3 — nộp mã, phát token thật:**

```
POST /auth/login/2fa  { code }
  Cookie: pre_auth_token=<uuid>

→ Core Service: Redis GET cache:pre_auth:{pre_auth_token} — không tồn tại/hết hạn, hoặc chưa
  từng gọi bước 2 (không có `method` trong cache) → 401
→ Verify code theo đúng `method` lưu trong cache (TOTP tính lại từ secret / SMS-EMAIL tra
  `otp_codes` / backup code tra `two_factor_backup_codes` — `method` lúc này là chuỗi
  `"BACKUP_CODE"`, KHÔNG phải giá trị của enum TwoFactorMethod, vì backup code không có dòng
  config riêng trong `two_factor_methods`; xem chi tiết mục "Verify lúc login" ở
  `services/03-core-service.md`)
→ Redis DEL cache:pre_auth:{pre_auth_token} ngay (dùng 1 lần, không tái sử dụng)
→ Tạo refresh_token + access_token THẬT như luồng bình thường (mục E.3)

← HTTP 200 + Set-Cookie access_token + refresh_token
  Set-Cookie: pre_auth_token=; Max-Age=0; Path=/auth/login/2fa   (clear ngay sau khi dùng xong,
  giống cách logout clear access/refresh_token — mục E.7)
```

**Quy tắc cứng:**
- `pre_auth_token` chỉ dùng được đúng 1 lần cho đúng 1 phiên login — không phải access token
  rút gọn, không mang bất kỳ quyền truy cập nghiệp vụ nào khác.
- Bước 3 mà cache chưa có `method` (user gọi thẳng `/auth/login/2fa` mà bỏ qua bước 2) → 401,
  bắt gọi `/auth/login/2fa/challenge` trước.
- Sai mã 2FA quá số lần cho phép (dùng lại `max_attempts` kiểu như `otp_codes`) → xoá
  `cache:pre_auth:{token}` + clear cookie (`Max-Age=0`), bắt đăng nhập lại từ bước nhập password.
- Endpoint `/auth/login/2fa/challenge` và `/auth/login/2fa` không đọc `X-User-Id` hay
  `access_token`/`refresh_token` cookie — toàn bộ danh tính lấy từ `pre_auth_token` cookie, vì
  tại thời điểm này user CHƯA được coi là đã đăng nhập.

## **E.10 Thu thập IP / thiết bị / địa điểm lúc login — ghi vào `user_sessions`**

Áp dụng cho mọi request tạo session (`/auth/login`, `/auth/login/2fa`, `/auth/oauth/callback`,
`/auth/refresh`) — Core Service phải điền đủ `ip_address`, `device_id`, `device_name`,
`platform`, `user_agent`, `login_country`, `login_city` của `user_sessions` (mục 3.2,
`03-core-service.md`) ngay lúc insert, không để trống rồi cập nhật sau.

**IP — chốt 1 lần duy nhất ở API Gateway, không tự đọc lại ở service khác:**
- API Gateway đọc IP thật của client từ `X-Forwarded-For` (hoặc `CF-Connecting-IP` nếu có
  Cloudflare phía trước) ngay tại tầng biên, rồi **ghi đè** thành header `X-Client-IP` khi
  forward xuống downstream.
- Downstream (Core Service) chỉ đọc `X-Client-IP` do Gateway set, **không bao giờ tin** giá trị
  `X-Client-IP`/`X-Forwarded-For` nếu client tự gửi lên thẳng — Gateway phải strip/ghi đè header
  này trước khi forward, tránh giả mạo IP để né rate-limit theo IP hoặc giả vờ login từ địa điểm
  khác.
- Core Service tuyệt đối không dùng địa chỉ IP của kết nối TCP tới nó (đó là IP của Gateway, sai).

**Địa điểm (`login_country`, `login_city`) — tự host, không gọi API bên thứ 3:**
- Core Service tự host **MaxMind GeoLite2-City** (`.mmdb`), lookup offline trong process bằng
  `com.maxmind.geoip2:geoip2` — không gọi ipapi.co/ip-api.com hay dịch vụ ngoài nào (tốn latency
  mỗi login, tốn phí khi scale, và gửi IP người dùng ra bên thứ 3 không cần thiết).
- File `.mmdb` cập nhật định kỳ (cron tải lại từ MaxMind mỗi tháng, cần free license key) —
  không bao giờ cache vĩnh viễn 1 bản build một lần rồi quên update.
- Độ chính xác chỉ ở mức thành phố/quốc gia — đây là giới hạn thật của GeoIP, không cố suy ra
  địa chỉ cụ thể hơn.
- **Implement**: `GeoIpService` (`com.chatapp.core.geoip`) load file `.mmdb` từ đường dẫn cấu
  hình `app.geoip.mmdb-path`, mặc định `${GEOIP_MMDB_PATH:./geoip/GeoLite2-City.mmdb}` — override
  bằng env `GEOIP_MMDB_PATH` khi cần path khác. Thiếu file/đường dẫn rỗng KHÔNG làm service
  crash — chỉ log warning và mọi lookup trả về rỗng (`login_country`/`login_city` = `null`),
  cùng triết lý graceful-degradation với `TotpSecretCipher` (dev/local không bắt buộc phải có
  file `.mmdb` vài chục MB, và file cũ/hỏng ở prod không được phép làm sập luôn cả luồng login).
  File `.mmdb` thật (tải từ MaxMind, cần free license key) KHÔNG commit vào repo (đã gitignore
  `services/*/geoip/*.mmdb`) — mỗi máy tự tải về đặt đúng `services/core-service/geoip/
  GeoLite2-City.mmdb` (path mặc định ở trên) nếu muốn geo lookup chạy thật ở local; nếu không có
  file, service tự fallback về `null`/`null` như mô tả ở trên, không cần set gì thêm.
- `login_country` lấy đúng `country.isoCode` (VARCHAR(2), VD "VN") từ response của thư viện
  `geoip2`, KHÔNG lấy `country.name` (tên đầy đủ dạng "Vietnam") — đúng kiểu cột đã chốt trong
  bảng `user_sessions` ở `03-core-service.md`.

**Audit log (`login_audit_logs`, chức năng #29)**: `LoginAuditLogService`
(`com.chatapp.core.audit`) — 1 method `record(...)` gọi tường minh tại từng call site
(`AuthServiceImpl.login()`/`verifyTwoFactor()`/`issueTokens()`), không dùng AOP/event listener,
vì các action khác nhau cần field khác nhau (LOGIN_FAILED không có `user_id` nếu identifier
không tồn tại; PASSWORD_CHANGE/LOGOUT/SESSION_REVOKE — CHƯA có call site vì các endpoint
logout/đổi password chưa được implement) — chỉ mới ghi được `LOGIN_SUCCESS`/`LOGIN_FAILED`.

**Thiết bị (`device_id`, `device_name`, `platform`) — nguồn khác nhau theo platform, KHÔNG có
cách server tự lấy chung cho cả web và mobile:**
- **Web**: browser không bao giờ cho lấy tên máy thật (chặn vì privacy/fingerprinting).
  `device_name` chỉ nên fallback = chuỗi parse từ header `User-Agent` (VD "Chrome on Windows"),
  parse bằng `nl.basjes.parse.useragent:yauaa` — không tự viết regex parse UA tay. `device_id`
  cho web là 1 UUID random client tự sinh, lưu `localStorage`, gửi kèm mỗi lần login để nhận
  diện lại đúng trình duyệt đó ở lần sau.
- **Mobile (iOS/Android)**: `device_name`/`device_id`/`platform` do **client app tự đọc bằng SDK
  hệ điều hành** (`UIDevice.current.name`, `Build.MODEL`) rồi gửi thẳng trong body request login
  — Core Service chỉ lưu lại, không tự suy ra được giá trị này từ header HTTP.

**Cảnh báo đăng nhập từ thiết bị/địa điểm mới:**
- Sau khi insert `user_sessions` thành công, Core Service so `device_id` với các session còn
  `is_active=true` gần nhất của user — nếu `device_id` chưa từng thấy (hoặc `login_country`
  khác hẳn lần gần nhất và khoảng cách thời gian giữa 2 lần login là bất hợp lý về mặt di
  chuyển) → publish `user.new_device_login` (`user.exchange`) kèm `device_name`, `login_city`,
  `login_country`, thời điểm login.
- Notification Service consume event này → gửi email/push cảnh báo bảo mật "Đăng nhập mới từ
  {device_name}, {login_city}" — KHÔNG chặn login (không phải cơ chế duyệt trước), chỉ thông
  báo sau khi đã tạo session, để user tự bấm "không phải tôi" → trigger `/auth/logout-all` nếu
  cần.

*─── Hết tài liệu ───*
