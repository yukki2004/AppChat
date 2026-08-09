# CLAUDE.md — Core Service

> Đọc `/CLAUDE.md` ở root trước. Service này GỘP 2 vai trò cũ: Identity Service + Group Service.
> Đây là service NHIỀU service khác gRPC vào nhất — coi như "nguồn sự thật" về user và nhóm.

## Vai trò

Auth (đăng ký/đăng nhập/OAuth2/2FA), profile, friend, block, privacy — cộng với toàn bộ quản
trị nhóm (tạo nhóm, phân quyền, invite/QR, duyệt thành viên, nickname, file/folder nhóm, sự
kiện/lịch hẹn nhóm).

## Tech stack

Java 25 / Spring Boot 4. PostgreSQL. Redis cho session/OTP/rate-limit login. RabbitMQ publish
qua `spring-boot-starter-amqp` (`RabbitTemplate`) — tự publish trong chính service, KHÔNG có
process/service Go riêng nào làm việc này (xem `skills/outbox-pattern.md`, đổi quyết định
2026-08-09: bỏ hẳn mô hình Go relay worker tách biệt).

## API docs

`springdoc-openapi-starter-webmvc-ui` đã bật (xem nguyên tắc #9 CLAUDE.md root) — UI ở
`/swagger-ui/index.html`, JSON ở `/v3/api-docs`, tự sinh từ `@RestController`/DTO hiện có,
không cần config thêm. Thêm controller/DTO mới thì Swagger tự cập nhật theo, không phải khai
báo tay ở đâu khác.

## Giao tiếp

- **gRPC expose** (service khác gọi vào): `RefreshAccessToken`, `GetUserPublicInfo`,
  `CheckFriendship`, `CheckBlock`, `GetPrivacySettings`, `GetGroupMembers`, `CheckGroupRole`,
  `GetGroupInfo`. Không còn `VerifySession` — access token là JWT, API Gateway/WS Gateway tự
  verify chữ ký tại chỗ bằng public key, không gọi gRPC mỗi request nữa (xem
  `docs/.../05-cookie-auth-flow.md`). Core Service giữ private key RS256, KHÔNG service nào
  khác được phát hành access token.
- **RabbitMQ publish**: `user.exchange` — `user.registered`, `user.profile_updated`,
  `user.blocked`, `user.new_device_login`, `user.logged_out_all` (WS Gateway subscribe để
  force-disconnect socket đang mở khi logout-all — xem `docs/.../05-cookie-auth-flow.md` E.8;
  **hầu hết vẫn `// TODO(outbox-pattern)` tại call site, CHỈ `user.qr_login_approved` (QR login,
  xem E.11) đã đi qua outbox thật** — bảng `outbox_events` + `OutboxEventPublisher` (insert,
  đăng ký publish sau khi commit) + `OutboxDispatcher` (bean riêng thật sự gọi `RabbitTemplate`,
  tránh lỗi self-invocation bỏ qua proxy `@Transactional`) + `OutboxRetryJob` (`@Scheduled`, quét
  lại `status=PENDING` làm lưới an toàn) đã hoạt động — tất cả nằm trong chính core-service,
  không có process/service nào khác. Các event TODO khác chưa được migrate sang cùng đợt — xem
  `skills/outbox-pattern.md`), `friend.request_sent`, `friend.accepted`,
  `friend.removed`, `user.block_set`, `user.block_removed` · `group.exchange` — `group.member_joined`, `group.member_removed`,
  `group.role_changed`, `group.join_request`, `group.deleted`, `group.event_created`,
  `group.event_reminder`.
- **RabbitMQ consume**: `presence.offline` (cập nhật `last_seen_at`), `media.upload_completed`
  / `media.quarantine` (dọn rác `group_files` khi file gốc bị xoá — xem mục Lưu ý bên dưới).

## Chức năng chính

Đăng ký/đăng nhập email+SĐT · OAuth2 (Google/Facebook/Apple) · 2FA (TOTP/SMS/Email) · quản lý
thiết bị đăng nhập · đổi mật khẩu (revoke toàn bộ session) · privacy settings · kết bạn/chặn ·
tạo nhóm, QR/invite link, duyệt thành viên, phân quyền OWNER/ADMIN/MEMBER · **nickname theo
nhóm** · **file/folder chung của nhóm** (chỉ lưu tham chiếu `media_upload_id`, không lưu file
thật) · **sự kiện/lịch hẹn nhóm + nhắc nhở** · **last_seen bền vững**.

## Cấu trúc thư mục — Entity/Repository/Config/Enum tập trung ở `base/`, phần còn lại theo domain

Core Service gộp rất nhiều domain (auth, profile, friend, block, privacy, group...). Quyết định
hiện tại (áp dụng cho Core Service trước, các service Java khác — messaging/social — chưa chốt,
tự quyết riêng khi tới lượt): **Entity, Repository, Enum lưu DB, và class `@Configuration`/
`@ConfigurationProperties` đều tập trung hết vào `base/`, bất kể chỉ 1 domain hay nhiều domain
dùng** — không còn kiểu "domain nào tự lo Entity/Repository của domain đó". Controller/Service/
DTO/Strategy thì vẫn theo domain như cũ, thêm domain mới = thêm 1 package mới cho phần này.

```
com.chatapp.core/
├── CoreServiceApplication.java
├── base/                      # hạ tầng dùng chung — KHÔNG phải 1 domain nghiệp vụ
│   ├── entity/                 # UserEntity, UserSessionEntity, TwoFactorMethodEntity, OtpCodeEntity,
│   │                            # FriendshipEntity, UserBlockEntity, CloseFriendEntity/CloseFriendId,
│   │                            # OutboxEventEntity (bảng outbox_events, xem skills/outbox-pattern.md)...
│   ├── repository/             # UserRepository, UserSessionRepository, TwoFactorMethodRepository,
│   │                            # FriendshipRepository, UserBlockRepository, CloseFriendRepository,
│   │                            # OutboxEventRepository (insert lúc publish() + đọc PENDING trong
│   │                            # chính OutboxRetryJob, không có service/process nào khác đọc bảng này)...
│   ├── constant/                # TwoFactorMethod, OtpPurpose, FriendshipStatus (+ converter tương ứng
│   │                            # mỗi enum — số cố định gán tay, xem skills/naming-conventions.md #3),
│   │                            # OutboxEventStatus (PENDING/PUBLISHED/FAILED, lưu chuỗi giống
│   │                            # LoginAuditEventType), RedisKeys, RabbitConstant (2 hằng số
│   │                            # String PHẲNG/event: `_EXCHANGE`/`_ROUTING_KEY` — KHÔNG có
│   │                            # `_QUEUE`, khai báo/bind queue là việc của consumer, không phải
│   │                            # publisher)
│   ├── message/                 # payload class cho MỌI event publish qua OutboxEventPublisher —
│   │   │                          tên lớp LUÔN kết thúc bằng "Message", CHỈ chứa field thật sự
│   │   │                          gửi đi trong JSON (không nhét field nội bộ như user_id vào
│   │   │                          đây — field đó truyền thẳng qua tham số aggregateId/
│   │   │                          aggregateType của publish(), xem OutboxEventPublisher.java) —
│   │   │                          1 subfolder/domain, KHÔNG gộp chung 1 file (message/qrlogin/ làm mẫu)
│   │   └── qrlogin/
│   │       └── QrLoginApprovedMessage.java   # chỉ có field qrToken
│   ├── config/                  # JwtProperties, OtpProperties, TotpProperties, PasswordEncoderConfig
│   ├── OutboxEventPublisher.java  # publish(exchange, routingKey, aggregateId, aggregateType,
│   │                            # payload) — 5 tham số tường minh, không gói vào 1 interface/
│   │                            # record trung gian (chỉ 1 message hiện có, thêm abstraction
│   │                            # sớm không đáng). Insert outbox_events trong transaction hiện
│   │                            # tại của caller, đăng ký publish ngay sau khi transaction đó
│   │                            # commit — entry point duy nhất cho outbox pattern, không publish
│   │                            # RabbitMQ trực tiếp ở đâu khác (skills/outbox-pattern.md)
│   ├── OutboxDispatcher.java      # bean riêng thật sự gọi RabbitTemplate + update status — tách
│   │                            # khỏi OutboxEventPublisher để tránh self-invocation bỏ qua
│   │                            # proxy @Transactional; dùng chung bởi cả publish ngay lẫn
│   │                            # scheduler/OutboxRetryJob.java
│   ├── UserResponse.java         # public user DTO (id/username/displayName/avatarUrl) — dùng chung
│   │                            # cho auth/ VÀ friend/, không phải bản riêng của domain nào
│   └── ApiResponse.java, ApiError.java   # response envelope chung cho MỌI endpoint (trừ /health)
├── auth/                      # register/login + luồng login-2FA (challenge/submit) — Controller/
│   │                            # Service/DTO/PreAuthTokenService, KHÔNG có Entity/Repository riêng
│   ├── AuthController.java, AuthService.java (interface) / impl/AuthServiceImpl.java
│   ├── AuthCookieBuilder.java     # set/clear access_token+refresh_token cookie — dùng chung
│   │                                giữa AuthController VÀ OAuthController (auth/oauth/), tránh
│   │                                lệch cấu hình cookie (HttpOnly/Secure/SameSite) giữa 2 nơi
│   ├── PreAuthTokenService.java
│   ├── dto/request/, dto/response/
│   └── oauth/                  # OAuth2 login (#4 Google đã code, #5-6 Facebook/Apple chưa) —
│       │                          strategy pattern giống twofactor/strategy/, xem "Lưu ý khi
│       │                          code" #15 — Entity/Repository (UserOAuthProviderEntity) vẫn ở
│       │                          base/ theo đúng quy tắc chung, KHÔNG nằm trong oauth/
│       ├── OAuthController.java, OAuthService.java (interface) / service/OAuthServiceImpl.java
│       ├── strategy/            # OAuthProviderStrategy + OAuthProviderDispatcher + 1 impl/provider
│       │                          (GoogleOAuthStrategy.java — thêm Facebook/Apple sau chỉ cần
│       │                          thêm 1 class impl mới, không sửa Dispatcher/Controller/Service)
│       └── dto/                 # OAuthUserInfo (record chuẩn hoá output mọi strategy), request/
│   └── qrlogin/                # QR login (WhatsApp-style, xem docs/.../05-cookie-auth-flow.md
│       │                          E.11) — thiết bị cũ confirm thay thế 2FA cho thiết bị mới,
│       │                          KHÔNG qua completeLogin(). Redis-only state (giống
│       │                          PreAuthTokenService), không có Entity/Repository riêng.
│       ├── QrLoginController.java, QrLoginService.java (interface) / service/QrLoginServiceImpl.java
│       ├── service/QrLoginSessionService.java   # Redis-only, cache:qr_login:{qr_token}
│       └── dto/response/                          # outbox payload KHÔNG nằm ở đây — xem
│                                                    # base/message/qrlogin/QrLoginApprovedMessage.java
├── twofactor/                 # strategy verify theo method (TOTP/EMAIL, SMS chưa làm) + luồng bật
│   │                            # 2FA (/2fa/*) — cũng KHÔNG có Entity/Repository riêng
│   ├── TwoFactorSettingsController.java / TwoFactorSettingsService.java
│   ├── TwoFactorChallengeDispatcher.java / TwoFactorChallengeStrategy.java (+ Totp.../Email... impl)
│   └── OtpCodeService.java, OtpMailSender.java, TotpSecretCipher.java, TotpCodeVerifier.java
├── friend/                    # gửi/chấp nhận/từ chối/huỷ lời mời/xoá bạn/danh sách bạn/close
│   │                            # friends (#19-23, #26) đã có, xem 03-core-service.md mục 3.1
│   ├── FriendController.java, FriendService.java (interface) / service/FriendServiceImpl.java
│   └── dto/request/, dto/response/
├── block/                     # chặn/gỡ chặn/danh sách đã chặn (#24-25) đã có — full block
│   │                            # only (v1), scope hẹp hơn (chặn tin/gọi riêng) là TODO chưa
│   │                            # lên lịch, xem TODO trong UserBlockEntity + 03-core-service.md
│   ├── BlockController.java, BlockService.java (interface) / service/BlockServiceImpl.java
│   └── dto/request/
├── profile/                   # CHỈ có #27 (xem profile người khác, read-only) — #15 (cập nhật
│   │                            # profile) CHƯA code, xem TODO privacy_settings (#16) trong
│   │                            # ProfileServiceImpl
│   ├── ProfileController.java, ProfileService.java (interface) / service/ProfileServiceImpl.java
├── privacy/                   # thêm sau, cùng khuôn mẫu (Controller/Service/DTO — Entity/
│                              # Repository mới thêm vào base/, không tạo trong domain)
├── group/                    # domain nặng nhất — có thể lại chia sub-package member/, file/, event/
├── security/                  # JwtKeyManager, JwtTokenProvider, JwksController — chỉ còn đúng phần
│                              # ký/verify JWT thật, không chứa Entity/Config (đã dời sang base/)
├── grpc/                      # IdentityGrpcService, GroupGrpcService (expose sau)
├── scheduler/                 # @Scheduled job — hiện chỉ OutboxRetryJob.java (lưới an toàn cho
│                              # outbox pattern, xem base/OutboxEventPublisher). Job mới sau này
│                              # (dọn rác, nhắc lịch group event...) cũng vào đây, KHÔNG để trong base/.
└── exception/                 # GlobalExceptionHandler + custom exception
    └── common/                 # AppException + ErrorCode — pattern cho lỗi MỚI từ giờ trở đi
                                 # (xem mục "Lưu ý khi code" #14), auth/2FA cũ vẫn giữ nguyên
                                 # exception riêng từng class, không hồi tố
```

Quy tắc: domain nào cần `UserEntity`/`UserRepository` thì import từ `base/entity/`, `base/
repository/` — KHÔNG copy field hay tạo entity riêng, kể cả entity chỉ domain đó dùng
(`TwoFactorMethodEntity`, `OtpCodeEntity` cũng nằm ở `base/` dù chỉ `twofactor/` dùng tới).
`security/` không phải 1 domain nghiệp vụ — nó là hạ tầng ký/verify JWT dùng chung cho mọi
domain, nên tách riêng khỏi `auth/` (domain `auth/` gọi vào `security/`, không tự ký JWT).

## Lưu ý khi code — 12 điểm dễ sai đã phát hiện lúc review spec

1. **Revoke access token khi logout/đổi mật khẩu/admin block**: KHÔNG được coi "revoke
   refresh_token trong DB" là xong việc — access token (JWT) đang lưu hành vẫn còn hiệu lực tới
   khi hết hạn tự nhiên (15 phút) nếu không set thêm `cache:jwt_revoked_before:{user_id}` (logout
   toàn bộ) hoặc `cache:jwt_blacklist:{jti}` (logout 1 thiết bị) trong Redis. Đây là bước BẮT
   BUỘC đi kèm mọi thao tác revoke session, xem `docs/.../05-cookie-auth-flow.md` mục E.6.
2. **Dọn rác `group_files`**: khi Media Service publish `media.upload_completed` bị xoá/quarantine
   sau đó, PHẢI có consumer soft-delete dòng `group_files` tương ứng — nếu không, thư viện
   nhóm sẽ hiện file chết trỏ tới upload không còn tồn tại.
3. **Chống nhắc sự kiện trùng**: bảng `group_events` cần field `reminded_at` — scheduler/cron
   quét event tới hạn PHẢI set `reminded_at = now()` ngay sau khi publish
   `group.event_reminder`, để tránh worker chạy nhiều lần cùng publish trùng thông báo.
4. **Cache invalidation khi update profile**: update `users.avatar_url`/`display_name` phải xoá
   `cache:user:{id}` ngay lập tức (không đợi TTL 5 phút tự hết), rồi mới publish
   `user.profile_updated` — nếu không, các service khác đọc cache cũ tới 5 phút.
5. **Refresh token PHẢI hash trước khi lưu DB** — `user_sessions.token_hash` = SHA-256(giá trị
   cookie), không bao giờ lưu giá trị gốc plaintext (khác với claim "session_id không mã hoá
   thông tin gì" nói ở CLAUDE.md gốc — đó là nói payload không chứa claim, không phải nói được
   phép lưu plaintext trong DB). Xem `docs/.../05-cookie-auth-flow.md`.
6. **2FA bắt buộc qua `pre_auth_token`, không phát access/refresh token thật trước khi verify
   xong mã 2FA** — xem `docs/.../05-cookie-auth-flow.md` mục E.9. Đây là lỗi dễ mắc nhất khi
   implement 2FA: phát token thật ngay sau password đúng sẽ vô hiệu hoá toàn bộ mục đích 2FA.
7. **Đổi email phải qua `pending_email`**, không ghi đè `users.email` ngay — chỉ cập nhật sau
   khi OTP gửi tới email MỚI được xác minh đúng, tránh khoá tài khoản nếu gõ sai email mới.
8. **OAuth account linking chỉ khi provider xác nhận email đã verified** — không tự động gộp
   tài khoản theo email trùng nếu provider không xác nhận `email_verified=true` (chống
   account-takeover). Xem ghi chú ở bảng `user_oauth_providers`.
9. **Thêm thành viên vào nhóm PHẢI check `who_can_add_to_group`** của người được thêm (gRPC
   `GetPrivacySettings`) trước khi insert `group_members` — nếu privacy là FRIENDS_ONLY (với
   người lạ) hoặc NOBODY, không được add thẳng mà phải tạo dòng `group_invite_pending` chờ
   người đó tự xác nhận. Bỏ qua bước check này là lỗ hổng spam-add-vào-nhóm phổ biến ở app chat.
10. **`groups.only_admin_can_send`**: đây là field Core Service lưu, nhưng bên ENFORCE là
    Messaging Service (gọi `CheckGroupRole` trước khi cho Member gửi tin) — Core chỉ cung cấp
    dữ liệu qua `GetGroupInfo`, không tự chặn gửi tin (đó không phải việc của Core).
11. **Report chỉ ở cấp user profile, không ôm luôn report tin nhắn/bài viết** — vi phạm
    database-per-service nếu Messaging/Social gọi ngược vào bảng `reports` của Core cho report
    nội dung cụ thể của họ. Report tin nhắn/bài viết PHẢI có bảng riêng ở đúng service đó.
12. **Không bao giờ publish `user.reported` cho phía người bị report biết** — chỉ đẩy vào kênh
    admin/nội bộ qua Notification Service, tránh lộ danh tính reporter gây trả thù.
13. **IP/thiết bị/địa điểm lúc login**: `ip_address` PHẢI đọc từ header `X-Client-IP` do API
    Gateway set (không tự đọc IP kết nối TCP tới Core Service — đó là IP của Gateway, sai).
    `login_country`/`login_city` resolve bằng MaxMind GeoLite2 tự host, không gọi API bên thứ 3.
    `device_id`/`device_name` mobile do client SDK gửi lên, web thì fallback parse `User-Agent`
    — không có cách server tự lấy "tên máy" thật cho web. Chi tiết đầy đủ + luồng cảnh báo
    "đăng nhập thiết bị mới": `docs/.../05-cookie-auth-flow.md` mục E.10.
14. **Lỗi nghiệp vụ MỚI dùng `AppException(ErrorCode.XXX)`, không tự tạo class exception riêng
    nữa** — `ErrorCode` (`exception/common/`) là enum tập trung mọi tình huống lỗi, mỗi giá trị
    mang `code` (int, chỉ dùng nội bộ/log, KHÔNG trả ra response), `message` (tiếng Anh), và
    `httpStatusCode`. `GlobalExceptionHandler` chỉ có đúng 1 `@ExceptionHandler(AppException.class)`
    xử lý chung, trả `ApiError.code = errorCode.name()` (String) — giữ đúng format response cũ.
    Exception riêng từng class kiểu cũ (`DuplicateUserException`, `InvalidCredentialsException`,
    `TwoFactorMethodNotEnabledException`, `TwoFactorMethodAlreadyEnabledException`,
    `OtpLockedException`, `SessionNotFoundException`, `RefreshTokenInvalidException`) ở domain
    `auth/`/`twofactor/` đã được migrate hết sang `AppException(ErrorCode.XXX)` — không còn
    exception riêng từng class nào trong 2 domain này nữa (đã áp dụng cho `friend/`, xem #19/#20,
    rồi tới `auth/`/`twofactor/`). Toàn bộ lỗi nghiệp vụ mới, bất kể domain nào, dùng đúng 1
    pattern `AppException(ErrorCode.XXX)` — không tạo lại class exception riêng theo domain nữa.
    Race condition khi insert entity có `@Id @GeneratedValue` sinh UUID trong bộ nhớ (không cần
    round-trip DB): `save()` KHÔNG insert ngay, Hibernate hoãn tới lúc flush — muốn bắt
    `DataIntegrityViolationException` ngay tại chỗ (VD: unique index chặn race condition) PHẢI
    dùng `saveAndFlush()`, không phải `save()`. Xem `FriendRequestResolver.insertNew()`.
15. **OAuth2 (#4-6): 1 provider/user, KHÔNG account linking** (product decision, khác thiết kế
    ban đầu — xem `docs/.../03-core-service.md` mục `user_oauth_providers`) — email OAuth trùng
    user đã tồn tại thì từ chối (`OAUTH_EMAIL_ALREADY_REGISTERED`), không tự gộp tài khoản.
    **2FA vẫn bắt buộc qua OAuth** nếu account đã bật — dùng chung `AuthService#completeLogin`
    với login password, không phát token thật ngay chỉ vì đã qua Google/Facebook/Apple. **Avatar
    từ provider không tự import vào CDN** — để `avatar_url = NULL` lúc tạo user mới qua OAuth,
    vì chỉ Media Service được gọi ra ngoài/gọi R2 (nguyên tắc #8 CLAUDE.md root), Core Service
    không tự tải ảnh từ URL Google. Redirect flow dùng "Kiểu B" (frontend tự hứng `code`, POST
    lên `POST /auth/oauth/{provider}/callback`), không phải backend tự redirect — xem
    `system/06-business-flows.md` F.2.
16. **User OAuth-only (`password_hash` NULL) phải set password trước khi bật được bất kỳ method
    2FA nào** — chặn ở đầu `TwoFactorSettingsService.setupTotp()`/`setupEmail()`
    (`ErrorCode.PASSWORD_REQUIRED_BEFORE_2FA`), dùng API mới `POST /auth/password/set`
    (`AuthService#setPassword` — khác `PUT /auth/password` vì không có old password để verify,
    chỉ chạy được khi `password_hash` hiện đang NULL). Lý do bắt buộc: `disableMethod()`/
    `regenerateBackupCodes()` re-auth bằng password — nếu không có password thì bật 2FA lên là
    tự kẹt vĩnh viễn, không bao giờ tắt được. `setPassword()` KHÔNG revoke session khác (khác
    `changePassword()`) vì không có credential cũ nào bị coi là lộ, chỉ đang thêm 1 cách đăng
    nhập mới.

## Nguyên tắc hiển thị tên/avatar (áp dụng cho service khác đọc dữ liệu từ đây)

Message/post/comment KHÔNG lưu snapshot tên/avatar người gửi — luôn hiển thị theo thông tin MỚI
NHẤT từ Core Service. Ngoại lệ CÓ CHỦ ĐÍCH: `reply_snapshot` và `forwarded_from_sender` bên
Messaging Service lưu snapshot tại thời điểm reply/forward để giữ ngữ cảnh lịch sử — không áp
dụng nguyên tắc "luôn mới nhất" cho 2 field này.
