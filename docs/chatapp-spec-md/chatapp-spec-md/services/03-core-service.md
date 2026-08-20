# **SERVICE 3 – CORE SERVICE**

Đảm nhận: Đăng ký, Đăng nhập, OAuth2, 2FA, Cookie Session, Block login, Quản lý thiết bị, Profile cơ bản, Privacy settings, Kết bạn, Chặn. Gộp User + Friend thành 1 service vì tất cả đều xoay quanh identity người dùng.

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Java 25 / Spring Boot 4 |
| **Database** | PostgreSQL |
| **Cache** | Redis (session, OTP, rate limit login) |
| **Auth** | HttpOnly Secure SameSite=Strict Cookie. access_token = JWT RS256 (TTL 15p, verify tại chỗ ở Gateway). refresh_token = UUID v4 ngẫu nhiên, lưu Redis+DB TTL rolling 30 ngày. Chi tiết: `system/05-cookie-auth-flow.md`. |
| **gRPC exposed** | RefreshAccessToken, GetUserPublicInfo, CheckFriendship, CheckBlock, GetPrivacySettings |
| **Outbox** | Bảng outbox_events nội bộ, Go relay worker đọc và publish RabbitMQ |

## **3.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Đăng ký bằng Email** | `POST /auth/register/otp` (username/password/displayName + email, KHÔNG kèm phone — email XOR phone bắt buộc) → validate trùng username/email → lưu tạm payload ở Redis (`cache:pending_register:{email}`, TTL = OTP TTL) → gửi OTP email (`OtpPurpose.REGISTER`, `user_id=NULL` vì user chưa tồn tại) → `POST /auth/register/verify` verify đúng mã mới thật sự tạo user (`email_verified_at=now()`) + session cookie. Sai mã/hết hạn → không tạo gì cả, không có user "chưa verify" nằm trong DB. |
| **2** | **Đăng ký bằng SĐT** | Y hệt #1, khác kênh: gửi OTP SMS (hiện SMS chưa có provider thật, tạm log ra thay vì gọi API thật — xem `OtpSmsSender`) thay vì email, verify xong set `phone_verified_at=now()`. |
| **3** | **Đăng nhập Email/SĐT + Password** | Verify password hash (BCrypt) → tạo session → set cookie. Ghi login_audit_logs. |
| **4** | **OAuth2 – Google** | Redirect Google consent → callback → upsert user_oauth_providers → session cookie. |
| **5** | **OAuth2 – Facebook** | Tương tự Google. Provider = FACEBOOK. |
| **6** | **OAuth2 – Apple** | Sign In With Apple. Provider = APPLE. Xử lý id_token JWT của Apple. |
| **7** | **Xác thực 2 bước – TOTP** | Bật 2FA: generate TOTP secret, QR code. Đăng nhập: yêu cầu nhập mã TOTP sau password. |
| **8** | **Xác thực 2 bước – SMS/Email** | Gửi OTP 6 số sau khi pass password. TTL 5 phút. |
| **9** | **Đăng xuất thiết bị hiện tại** | Xoá session Redis, clear cookie. |
| **10** | **Đăng xuất tất cả thiết bị** | Xoá toàn bộ session của user trong Redis, revoke tất cả refresh token. |
| **11** | **Đổi mật khẩu** | Verify password cũ → hash mới → revoke tất cả session. |
| **12** | **Quên mật khẩu / Reset** | Gửi OTP → verify → đặt password mới → revoke session. |
| **13** | **Block login tài khoản** | Admin set is_blocked=true → tất cả session bị invalidate ngay lập tức (Redis). |
| **14** | **Quản lý thiết bị đăng nhập** | Xem danh sách thiết bị (device_sessions), thu hồi 1 thiết bị cụ thể. |
| **15** | **Cập nhật Profile** | Đổi display_name, bio, avatar_url, cover_url, date_of_birth. |
| **16** | **Cài đặt Privacy** | Ai thấy trạng thái online, ai kết bạn được, ai nhắn tin, ai xem story, ai xem profile. |
| **17** | **Ẩn trạng thái theo khung giờ** | Lưu schedule_hidden_from/to (UTC time). Presence Service đọc qua gRPC để kiểm tra. |
| **18** | **Tìm kiếm user** | Full-text search display_name + exact match username. Trả PublicUserDTO. |
| **19** | **Gửi lời mời kết bạn** | Tạo friendship (status=PENDING). Publish event friend.request_sent. |
| **20** | **Chấp nhận kết bạn** | Update friendship status=ACCEPTED. Publish event friend.accepted. |
| **21** | **Từ chối / Huỷ lời mời** | Update status=REJECTED / CANCELLED. |
| **22** | **Xoá bạn bè** | Delete friendship record. Publish event friend.removed. |
| **23** | **Danh sách bạn bè** | Trả danh sách kèm PublicPresenceDTO (gRPC Presence). |
| **24** | **Chặn người dùng** | Insert user_blocks (scope=ALL). Publish event user.blocked. |
| **25** | **Gỡ chặn** | Delete user_blocks. Publish event user.unblocked. |
| **26** | **Danh sách bạn thân (Close Friends)** | Quản lý close_friends list – dùng cho story visibility. |
| **27** | **Xem profile người khác** | Trả thông tin theo privacy_settings. |
| **28** | **Backup codes 2FA** | Generate 8 backup codes, lưu hash, trả về lúc bật method 2FA đầu tiên. Regenerate được (re-auth password). Dùng thay TOTP/SMS/EMAIL khi mất quyền truy cập method chính. |
| **29** | **Audit Log truy cập** | Ghi mọi login, logout, đổi password vào login_audit_logs. |
| **30** | **Report người dùng** | Tạo `reports` (reported_type=USER), rate-limit số report gửi/ngày để chống lạm dụng, publish `user.reported` CHỈ cho kênh admin (không báo cho người bị report — tránh trả thù). Xem mục 3.16. |
| **31** | **Liên kết thêm Email/SĐT** | Account đăng ký bằng 1 trong 2 (email XOR phone) muốn thêm cái còn lại — KHÔNG làm lúc đăng ký. `POST /auth/link/otp` (X-User-Id, re-auth password + email HOẶC phone, cột đó phải đang NULL) → gửi OTP (`OtpPurpose.LINK_IDENTIFIER`, có `user_id` vì account đã tồn tại) → `POST /auth/link/verify` verify đúng mã → ghi `email`/`phone` + `*_verified_at=now()`. Sau khi liên kết, login được bằng cả 2 định danh (login vốn đã tra cả username/email/phone qua 1 hàm chung, không cần sửa gì thêm). Khác `pending_email` (#7 ghi chú trong CLAUDE.md service) — đó là THAY email đã có, đây là ĐIỀN vào cột đang trống. |

## **3.2 Naming Convention – Java**

### **Class**

// Entity

public class UserEntity { }          // suffix Entity cho JPA

public class FriendshipEntity { }

// DTO

public record PublicUserDTO(...) { } // record cho immutable DTO

public record CreateUserRequest(...) { }

// Service

public class AuthService { }

public class FriendshipService { }

// Repository

public interface UserRepository extends JpaRepository<UserEntity, UUID> { }

// Controller

@RestController

public class AuthController { }

// Enum

public enum FriendshipStatus { PENDING, ACCEPTED, REJECTED, CANCELLED }

// Constants

public final class SessionConstants { public static final int TTL_DAYS = 30; }

### **Variable**

private String sessionId;       // camelCase private field

public UUID userId;             // camelCase public field

## **3.3 Enums**

**Enum: FriendshipStatus**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **PENDING** | Đã gửi lời mời, chờ phản hồi |
| **ACCEPTED** | Đã là bạn bè |
| **REJECTED** | Từ chối lời mời |
| **CANCELLED** | Người gửi huỷ lời mời |

**Enum: OAuthProvider**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **GOOGLE** | Đăng nhập Google |
| **FACEBOOK** | Đăng nhập Facebook |
| **APPLE** | Đăng nhập Apple |

**Enum: OtpPurpose**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **REGISTER** | Xác minh đăng ký |
| **LOGIN_2FA** | Xác thực 2 bước khi đăng nhập (method SMS/EMAIL) |
| **RESET_PASSWORD** | Đặt lại mật khẩu |
| **CHANGE_EMAIL** | Đổi email |
| **ENABLE_2FA** | Xác minh số điện thoại/email trước khi bật làm method 2FA (SMS/EMAIL) — không áp dụng cho TOTP (verify bằng mã app, không qua OTP gửi SMS/email) |
| **LINK_IDENTIFIER** | Liên kết thêm email/SĐT vào account đã đăng ký bằng cái còn lại (mục 3.1 #31) — khác CHANGE_EMAIL (đó là thay email đã có qua `pending_email`, đây là điền vào cột đang NULL) |

**Enum: PrivacyVisibility**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **EVERYONE** | Mọi người |
| **FRIENDS_ONLY** | Chỉ bạn bè |
| **FRIENDS_OF_FRIENDS** | Bạn của bạn |
| **ONLY_ME** | Chỉ mình tôi |

**Enum: GroupInviteVisibility**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **EVERYONE** | Ai cũng thêm được vào nhóm trực tiếp |
| **FRIENDS_ONLY** | Chỉ bạn bè mới thêm thẳng được, người lạ add → tạo lời mời chờ xác nhận |
| **NOBODY** | Không ai thêm thẳng được, luôn tạo lời mời chờ xác nhận (kể cả bạn bè) |

**Enum: ReportReason**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **SPAM** | Quảng cáo/rác |
| **HARASSMENT** | Quấy rối |
| **FAKE_ACCOUNT** | Tài khoản giả mạo |
| **INAPPROPRIATE_CONTENT** | Nội dung không phù hợp (avatar/bio) |
| **OTHER** | Khác — bắt buộc kèm `description` |

**Enum: ReportStatus**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **PENDING** | Chờ admin xem xét |
| **REVIEWING** | Đang xử lý |
| **RESOLVED** | Đã xử lý (có hành động, VD block) |
| **DISMISSED** | Xem xét xong, không vi phạm |

**Enum: OnlineStatusMode**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **PUBLIC** | Hiển thị cho tất cả bạn bè |
| **SCHEDULED** | Ẩn theo khung giờ |
| **HIDDEN** | Luôn ẩn |

## **3.4 Database Schema**

### **ð users  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK – khoá chính |
| username | VARCHAR(50) UNIQUE | NO | – | Username unique, lowercase, không dấu |
| email | VARCHAR(255) UNIQUE | YES | NULL | Email đăng ký (null nếu chỉ phone) |
| email_verified_at | TIMESTAMPTZ | YES | NULL | UTC – null = chưa xác minh |
| pending_email | VARCHAR(255) | YES | NULL | Email mới đang chờ xác minh khi đổi email — KHÔNG ghi đè `email` cho tới khi OTP đúng, tránh khoá tài khoản nếu gõ sai email mới |
| phone | VARCHAR(20) UNIQUE | YES | NULL | Số điện thoại E.164 format |
| phone_verified_at | TIMESTAMPTZ | YES | NULL | UTC – null = chưa xác minh |
| password_hash | VARCHAR(255) | YES | NULL | BCrypt hash (null nếu OAuth only) |
| display_name | VARCHAR(100) | NO | – | Tên hiển thị |
| avatar_url | VARCHAR(500) | YES | NULL | Cloudflare CDN URL |
| cover_url | VARCHAR(500) | YES | NULL | Ảnh bìa CDN URL |
| bio | TEXT | YES | NULL | Giới thiệu bản thân |
| date_of_birth | DATE | YES | NULL | Ngày sinh |
| is_private | BOOLEAN | NO | false | Trang cá nhân riêng tư |
| is_active | BOOLEAN | NO | true | Tài khoản đang hoạt động |
| is_blocked | BOOLEAN | NO | false | Bị admin khoá tài khoản |
| blocked_reason | TEXT | YES | NULL | Lý do khoá |
| blocked_at | TIMESTAMPTZ | YES | NULL | Thời điểm bị khoá (UTC) |
| last_login_at | TIMESTAMPTZ | YES | NULL | Đăng nhập lần cuối (UTC) |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC – cập nhật tự động trigger |
| deleted_at | TIMESTAMPTZ | YES | NULL | Soft delete (UTC) |

### **ð user_oauth_providers  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users, UNIQUE | NO | – | Người dùng — UNIQUE vì 1 user chỉ được đúng 1 provider (xem quy tắc dưới) |
| provider | VARCHAR(20) | NO | – | GOOGLE / FACEBOOK / APPLE |
| **provider_user_id** | VARCHAR(255) | NO | – | ID từ provider, UNIQUE per provider |
| provider_email | VARCHAR(255) | YES | NULL | Email từ provider |
| access_token_enc | TEXT | YES | NULL | Encrypted access token — hiện chưa dùng (không có luồng nào gọi lại API provider sau login), để NULL |
| refresh_token_enc | TEXT | YES | NULL | Encrypted refresh token — hiện chưa dùng, để NULL |
| token_expires_at | TIMESTAMPTZ | YES | NULL | Hết hạn access token (UTC) — hiện chưa dùng, để NULL |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

**Quy tắc: 1 provider/user, KHÔNG account linking (product decision).** Khác thiết kế ban đầu
(đã cân nhắc rồi bỏ) — mỗi user chỉ được liên kết đúng 1 OAuth provider (UNIQUE `user_id`, không
phải 1-nhiều). Khi OAuth callback trả về email trùng với 1 user đã tồn tại (đăng ký bằng
email/password hoặc provider khác), **từ chối luôn** (`ErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED`),
không tự động gộp/link tài khoản — trả lỗi gợi ý đăng nhập bằng phương thức gốc. Lý do: tránh
việc phải xây thêm màn hình "connected accounts" để user tự gỡ/quản lý nhiều provider (chưa có
trong scope #4-6), và đơn giản hoá luồng OAuth ban đầu. `emailVerified` từ provider (Google/
Facebook có field `email_verified`, Apple luôn coi là verified) hiện chỉ dùng để quyết định có
tạo user mới hay không trong nội bộ `OAuthServiceImpl`, không dùng để tự động link.

**2FA vẫn áp dụng cho login OAuth** (product decision): nếu account đã bật TOTP/Email 2FA, login
qua Google/Facebook/Apple vẫn phải qua `pre_auth_token` + `TwoFactorChallengeDispatcher` giống hệt
login password, không phát access/refresh token thật ngay — xem `AuthService#completeLogin`
(dùng chung giữa `AuthServiceImpl.login()` và `OAuthServiceImpl.loginWithCallback()`).

**Redirect flow — "Kiểu B"**: frontend tự redirect sang provider và tự bắt `code` từ redirect trở
về (không phải Core Service hứng redirect trực tiếp), rồi POST `code` lên
`POST /auth/oauth/{provider}/callback` như 1 REST API JSON bình thường — xem
`system/06-business-flows.md` F.2. Chọn kiểu này (thay vì để backend tự redirect) vì dùng chung
được 1 API cho cả web và mobile app sau này (mobile native SDK cũng chỉ đưa `code` cho app rồi
app tự POST lên, không có khái niệm "provider redirect thẳng vào backend" trên mobile).

**User OAuth-only (`password_hash` NULL) phải set password trước khi bật được 2FA (#7-8)** —
API mới `POST /auth/password/set` (khác `PUT /auth/password` #11: không có old password để
verify, chỉ chạy được khi `password_hash` hiện đang NULL). `TwoFactorSettingsService.setupTotp()`/
`setupEmail()` chặn ngay đầu nếu `password_hash == null` (`ErrorCode.PASSWORD_REQUIRED_BEFORE_2FA`).
Lý do: tắt 1 method 2FA / tạo lại backup code đều re-auth bằng password — không có password thì
bật 2FA lên là tự kẹt tài khoản vĩnh viễn, không bao giờ tắt được. `setPassword()` không revoke
session khác (khác `changePassword()`) vì không có credential cũ nào bị coi là lộ.

**Avatar từ provider không được tự động import vào CDN của mình lúc đăng ký OAuth** — `avatar_url`
để NULL cho user mới tạo qua OAuth, giống hệt user đăng ký email/password mới (chưa có avatar).
Lý do: `avatar_url` phải là CDN URL của chính mình (Cloudflare), và theo nguyên tắc #8 CLAUDE.md
root, chỉ Media Service được gọi ra ngoài tải file/gọi R2 — Core Service không được tự tải ảnh từ
URL của Google rồi lưu thẳng. Import avatar từ provider (nếu làm sau) sẽ là 1 luồng riêng: Core
publish event `user.avatar_import_requested` (side-effect, không cần response ngay — đúng
RabbitMQ theo `skills/service-communication.md`) để Media Service tự tải + upload lên R2, rồi
báo lại qua `media.upload_completed` (event đã có sẵn) với `context_type=avatar` — TODO, chưa
lên lịch làm.

### **ð user_sessions  [PostgreSQL (index Redis primary)] — lưu refresh_token, KHÔNG phải access_token**

> access_token (JWT) không lưu server-side (self-contained, verify bằng chữ ký). Bảng này lưu
> refresh_token — xem `system/05-cookie-auth-flow.md`.
>
> **Không lưu refresh_token dạng plaintext** — cookie giữ giá trị gốc (UUID v4), DB/Redis chỉ
> lưu `SHA-256(refresh_token)` làm khoá tra cứu, giống cách `otp_codes.code_hash` và
> `two_factor_backup_codes.codes_hash` đã làm. Verify bằng cách hash lại giá trị nhận từ cookie
> rồi so `token_hash` — không bao giờ SELECT ngược từ hash ra giá trị gốc (là hash 1 chiều, đúng
> ý). Lý do: nếu DB bị đọc trộm (backup leak, SQL injection, insider), refresh_token thật có
> quyền lực tương đương password (sống 30 ngày) — không được để lộ giá trị dùng được ngay.

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK nội bộ, KHÔNG phải giá trị refresh_token |
| **token_hash** | VARCHAR(64) UNIQUE | NO | – | SHA-256(refresh_token) — refresh_token gốc chỉ nằm trong cookie, không lưu DB |
| **user_id** | UUID FK→users | NO | – | Chủ session |
| **device_id** | VARCHAR(100) | YES | NULL | Device fingerprint |
| device_name | VARCHAR(150) | YES | NULL | Tên thiết bị (iPhone 15, Chrome/Mac) |
| platform | VARCHAR(20) | YES | NULL | ios / android / web |
| ip_address | INET | YES | NULL | IP đăng nhập — lấy từ header `X-Client-IP` do API Gateway set, xem `05-cookie-auth-flow.md` E.10 |
| user_agent | TEXT | YES | NULL | Browser / app user agent |
| login_country | VARCHAR(2) | YES | NULL | ISO country code, resolve từ `ip_address` bằng MaxMind GeoLite2 lúc login — xem E.10 |
| login_city | VARCHAR(100) | YES | NULL | Resolve cùng lúc với `login_country`, chỉ chính xác tới mức thành phố |
| is_active | BOOLEAN | NO | true | Session còn hiệu lực |
| expires_at | TIMESTAMPTZ | NO | – | UTC – hết hạn (now + 30d) |
| last_active_at | TIMESTAMPTZ | NO | now() | UTC – hoạt động cuối |
| revoked_at | TIMESTAMPTZ | YES | NULL | UTC – bị thu hồi |
| revoke_reason | VARCHAR(50) | YES | NULL | `USER_LOGOUT` / `USER_LOGOUT_ALL` / `USER_REVOKED_REMOTE` (đăng xuất 1 thiết bị khác) / `ROTATED` (refresh_token bị thay bằng token mới, xem `system/05-cookie-auth-flow.md` E.5) / `REFRESH_TOKEN_REUSE_DETECTED` (token đã `ROTATED` bị dùng lại — nghi bị đánh cắp, revoke toàn bộ session của user) / `PASSWORD_CHANGED` (đổi mật khẩu hoặc reset qua quên mật khẩu — revoke toàn bộ session, xem chức năng #11-12) |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| access_token_jti | VARCHAR(36) | YES | NULL | `jti` của access_token đang sống ứng với session này — gán lại mỗi lần token xoay vòng (login/refresh). Cho phép `DELETE /auth/sessions/{sessionId}` blacklist ngay access_token của thiết bị đó (`cache:jwt_blacklist:{jti}`) thay vì đợi hết hạn tự nhiên (tối đa 15 phút). NULL với các dòng tạo trước migration `V20260808150000`. |

### **ð otp_codes  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| target | VARCHAR(255) | NO | – | Email hoặc phone nhận OTP |
| code_hash | VARCHAR(255) | NO | – | SHA-256 hash của mã 6 số |
| purpose | SMALLINT | NO | – | Enum OtpPurpose — lưu số cố định gán tay, không phải chuỗi/ordinal, xem `skills/naming-conventions.md` #3 |
| **user_id** | UUID FK→users | YES | NULL | Null nếu chưa tạo account |
| attempt_count | INT | NO | 0 | Số lần nhập sai |
| max_attempts | INT | NO | 5 | Giới hạn thử |
| expires_at | TIMESTAMPTZ | NO | – | UTC (now + 5 phút) |
| used_at | TIMESTAMPTZ | YES | NULL | UTC – đã sử dụng |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

**Khoá tạm sau khi sai đủ `max_attempts`** — chỉ khoá đúng dòng đó (DB) là chưa đủ, vì gọi lại
`generate()` sẽ tạo dòng MỚI với `attempt_count=0`, bên verify chỉ tra dòng mới nhất
(`findTop...OrderByCreatedAtDesc`) nên coi như giới hạn 5 lần vô nghĩa nếu cho phép xin mã mới
ngay lập tức. Bù bằng Redis, tách khỏi vòng đời từng dòng `otp_codes`:
```
cache:otp_lockout:{purpose}:{target} = "1", TTL = app.otp.lockout-seconds (mặc định 1800s = 30
phút, cấu hình qua OTP_LOCKOUT_SECONDS)
```
Set key này ngay khi lần sai thứ `max_attempts` xảy ra (trong `verify()`). Cả `generate()` lẫn
`verify()` đều check key này trước, có thì chặn (`OtpLockedException` → 429 `OTP_LOCKED`) —
chặn cả "xin mã mới" chứ không chỉ "verify mã cũ".

**Tại 1 thời điểm chỉ có đúng 1 mã "sống" cho mỗi (target, purpose, user_id).** `verify()` chỉ
tra dòng mới nhất chưa dùng (`findTop...OrderByCreatedAtDesc`) — nếu 2 request `generate()` xảy
ra gần nhau (VD user bấm "gửi lại mã", hoặc 2 thiết bị cùng đăng nhập 1 tài khoản), mã CŨ vẫn
còn hạn nhưng sẽ vĩnh viễn không verify được nữa vì bị mã MỚI hơn "che" mất trong query — user
thấy như bug "mã đúng nhưng báo sai". Để tránh: `generate()` luôn `UPDATE ... SET used_at = now()`
(gọi `OtpCodeEntity.invalidate()`, cùng cơ chế cột với `markUsed()` nhưng khác ý nghĩa — "bị ghi
đè" chứ không phải "verify thành công") cho MỌI dòng `used_at IS NULL` cùng (target, purpose,
user_id) NGAY TRƯỚC khi tạo dòng mới — đảm bảo tại 1 thời điểm chỉ tồn tại đúng 1 mã hợp lệ.

### **ð two_factor_methods  [PostgreSQL]**

Một user có thể bật ĐỒNG THỜI nhiều method (TOTP + SMS + EMAIL) — 1 dòng / (user_id, method),
không phải 1 dòng / user. Lý do chọn multi-method thay vì chỉ 1 method độc quyền: giống cách
Google/GitHub/Microsoft đang làm (đăng ký song song nhiều method, có "thử cách khác" lúc login
nếu method chính không dùng được).

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users | NO | – | Chủ method — KHÔNG unique 1 mình, unique theo cặp (user_id, method) |
| method | VARCHAR(10) | NO | – | TOTP / SMS / EMAIL |
| totp_secret_enc | VARCHAR(255) | YES | NULL | Encrypted TOTP secret (AES-256) — chỉ set khi method=TOTP |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

Ràng buộc: `UNIQUE (user_id, method)` — 1 user không thể có 2 dòng cùng method.

> **Lazy-insert, không tạo sẵn dòng lúc register.** Đại đa số user không bao giờ bật 2FA, nên
> KHÔNG insert dòng nào mặc định cho mọi user lúc tạo tài khoản — chỉ insert khi user chủ động
> bật 1 method cụ thể. Ở bước login, `SELECT ... WHERE user_id = ?` không trả dòng nào ⇒ coi
> như chưa bật 2FA, bỏ qua bước 2FA, phát access_token/refresh_token thật luôn — KHÔNG được coi
> "không có dòng nào" là lỗi hay tự động insert dòng mới ở luồng login.
>
> **Không tự chọn method hộ user khi có nhiều dòng.** `/auth/login` chỉ trả `available_methods`
> (sắp xếp gợi ý theo độ mạnh **TOTP > EMAIL > SMS** — SMS dễ bị SIM-swap nhất), KHÔNG tự dispatch
> challenge hay gửi OTP cho method nào. User phải chủ động chọn qua
> `/auth/login/2fa/challenge` rồi Core Service mới gửi mã cho đúng method đó — xem
> `system/05-cookie-auth-flow.md` E.9.

**Luồng bật 2FA (`/2fa/*`, cần đã đăng nhập — đọc `X-User-Id` do Gateway set, không đọc lại
JWT)** — KHÔNG insert `two_factor_methods` ngay lúc setup, chỉ insert sau khi verify đúng 1 lần,
tránh user tự khoá mình ngoài tài khoản vì cấu hình sai:

```
POST /2fa/totp/setup    (X-User-Id)
  → sinh secret ngẫu nhiên (20 byte) → Base32 encode
  → lưu TẠM cache:pending_totp_secret:{user_id} (Redis, TTL 10 phút) — CHƯA ghi DB
  ← { secret, otpauth_uri }   (client tự render QR từ otpauth_uri)

POST /2fa/totp/confirm  { code }   (X-User-Id)
  → đọc secret tạm từ Redis, tính lại mã TOTP tại thời điểm hiện tại, so với `code`
  → đúng: INSERT two_factor_methods (method=TOTP, totp_secret_enc = encrypt(secret)), xoá key tạm
  → sai/hết hạn: 401, không insert gì

POST /2fa/email/setup   (X-User-Id)
  → sinh + gửi OTP tới email hiện tại của user (purpose=ENABLE_2FA, dùng lại otp_codes)

POST /2fa/email/confirm { code }   (X-User-Id)
  → verify OTP (purpose=ENABLE_2FA) → đúng: INSERT two_factor_methods (method=EMAIL)
```

Gọi setup/confirm khi method đó **đã bật rồi** → 409 `METHOD_ALREADY_ENABLED`. SMS chưa có
endpoint tương ứng (chưa triển khai `SmsTwoFactorStrategy`).

**Tắt 1 method (`DELETE /2fa/{method}`, cần đã đăng nhập)**:

```
DELETE /2fa/totp   { password }   (X-User-Id)

→ Verify LẠI password (KHÔNG bắt nhập mã của chính method đang xoá — lý do phổ biến nhất user
  xoá 1 method là ĐÃ MẤT quyền truy cập nó, VD mất điện thoại cài TOTP, nên không thể bắt nhập
  lại đúng cái vừa mất)
→ Sai password → 401
→ Đúng → DELETE dòng two_factor_methods tương ứng
```

Cho phép xoá hết TẤT CẢ method (kể cả method cuối cùng, không bắt buộc còn lại ≥1) — chủ đích
giữ đơn giản, không siết chặt kiểu ngân hàng. Vì xoá 2FA là hành động nhạy cảm (kẻ tấn công
chiếm được session tạm thời có thể tắt 2FA để giữ quyền truy cập vĩnh viễn), lẽ ra phải publish
event cảnh báo bảo mật (`user.two_factor_disabled` qua `user.exchange`) cho Notification Service
báo cho chủ tài khoản biết dù không phải họ tự tắt — **chưa làm** vì outbox pattern chưa wire
cho Core Service, đang để TODO ở `TwoFactorSettingsService.disableMethod()`.

### **ð two_factor_backup_codes  [PostgreSQL]**

Backup codes là lưới an toàn CẤP TÀI KHOẢN, không thuộc về 1 method cụ thể nào (dùng được bất kể
user đang bật TOTP hay SMS hay EMAIL) — nên tách bảng riêng khỏi `two_factor_methods`, không
nhét chung vào 1 trong các dòng method.

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **user_id** | UUID PK FK→users | NO | – | 1-1 với users |
| codes_hash | TEXT[] | NO | – | Mảng SHA-256 hash của 8 backup codes |
| codes_used | INT | NO | 0 | Số backup code đã dùng |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |
| version | BIGINT | NO | 0 | Optimistic lock — xem lý do ở mục "Cơ chế tiêu thụ 1 mã" bên dưới |

Lazy-insert giống `two_factor_methods` — chỉ tạo dòng lúc user bật method 2FA đầu tiên (sinh
kèm 8 backup code lúc đó), không tạo sẵn cho mọi user. `POST /2fa/totp/confirm` và
`POST /2fa/email/confirm` trả kèm field `backupCodes` (8 mã plaintext, dạng `XXXX-XXXX`) trong
response — CHỈ có giá trị (khác `null`) đúng 1 lần, ở lần bật method 2FA đầu tiên; bật thêm
method thứ 2/3 sau đó thì `backupCodes` trả về `null` vì bộ mã cũ vẫn còn hiệu lực, không sinh
lại. Plaintext code không bao giờ lưu lại bất cứ đâu sau response đó — chỉ `codes_hash` (SHA-256)
được persist.

**Cơ chế tiêu thụ 1 mã (`consume`)**: KHÔNG có cột đánh dấu "used" riêng cho từng mã trong mảng
`codes_hash` — dùng 1 mã thì xoá hash tương ứng khỏi mảng và tăng `codes_used` lên 1, nên luôn
đúng bất biến `codes_used = 8 - len(codes_hash)`. Cách này tận dụng đúng kiểu mảng có sẵn, không
cần thêm cột/bảng phụ để track trạng thái từng mã.

**Optimistic lock (`version`)**: `consume` đọc nguyên mảng `codes_hash`, lọc bỏ 1 phần tử trong
memory rồi ghi đè LẠI TOÀN BỘ cột — không phải `array_remove` ở tầng SQL. Nếu 2 request verify
chạy đồng thời cho cùng user (VD 2 thiết bị cùng login gần như cùng lúc) mà không có `version`,
request lưu SAU sẽ ghi đè mất thay đổi của request lưu TRƯỚC (lost update) — mã đã tiêu thụ có
thể "sống lại" thành chưa dùng. `version` (JPA `@Version`) buộc Hibernate reject UPDATE nào dựa
trên bản đọc đã cũ; `TwoFactorBackupCodeService.verify()` bắt `OptimisticLockingFailureException`
và tự đọc lại + verify lại tối đa 3 lần thay vì để lỗi lộ ra ngoài — an toàn vì mỗi lần retry đều
check lại `matches()` trên mảng mới nhất, nên nếu mã vừa bị request kia tiêu thụ mất thì lần
retry sẽ đúng đắn trả về sai (không consume trùng).

**Regenerate**: `POST /2fa/backup-codes/regenerate  { password }` (X-User-Id) — bắt buộc re-auth
bằng password (cùng lý do với xoá method ở trên: session bị chiếm tạm thời không được tự cấp cho
mình 1 bộ backup code mới). Yêu cầu tài khoản đang có ít nhất 1 method 2FA bật (không thì trả lỗi
`METHOD_NOT_ENABLED`). Sinh 8 mã hoàn toàn mới, GHI ĐÈ toàn bộ — mã cũ (kể cả mã cũ chưa dùng) vô
hiệu ngay lập tức, không có kiểu "top-up" thêm mã lẻ. User có thể gọi endpoint này bất kỳ lúc nào,
không bắt buộc phải dùng hết 8 mã mới cho gọi (VD: lỡ chụp màn hình mã cũ, đổi máy...). Trả về
cùng format `{ backupCodes: [...] }` như lúc bật method đầu tiên.
> TODO chưa làm (giống TODO ở mục xoá method trên): regenerate cũng là hành động nhạy cảm, lẽ ra
> phải publish security alert cho chủ tài khoản biết — chưa làm vì outbox pattern chưa wire cho
> Core Service.

**Verify lúc login (`/auth/login/2fa`)**: backup code là 1 nhánh ngang hàng với TOTP/SMS/EMAIL ở
bước chọn method (`/auth/login/2fa/challenge`), nhưng KHÔNG phải 1 giá trị của enum
`TwoFactorMethod` — đây là pseudo-method `"BACKUP_CODE"` xử lý riêng ở `AuthServiceImpl`, không
đi qua `TwoFactorChallengeDispatcher`/`TwoFactorMethodEntity` vì backup code không có dòng config
riêng trong `two_factor_methods`. Chỉ xuất hiện trong `available_methods` trả về ở bước 1 nếu tài
khoản đã có `two_factor_backup_codes` (tức đã từng bật ít nhất 1 method 2FA). Verify đúng → tiêu
thụ 1 mã (xem cơ chế `consume` ở trên) → phát access/refresh token thật như các method khác.

### **ð user_privacy_settings  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **user_id** | UUID PK FK→users | NO | – | 1-1 với users |
| online_status_mode | VARCHAR(20) | NO | PUBLIC | Enum OnlineStatusMode |
| schedule_hidden_from | TIME | YES | NULL | Giờ bắt đầu ẩn (UTC time) |
| schedule_hidden_to | TIME | YES | NULL | Giờ kết thúc ẩn (UTC time) |
| who_can_add_friend | VARCHAR(20) | NO | EVERYONE | Enum PrivacyVisibility |
| who_can_message | VARCHAR(20) | NO | EVERYONE | EVERYONE / FRIENDS_ONLY |
| who_can_add_to_group | VARCHAR(20) | NO | FRIENDS_ONLY | Enum GroupInviteVisibility (riêng, KHÔNG dùng chung PrivacyVisibility vì có giá trị NOBODY khác ONLY_ME về ngữ cảnh) — xem quy tắc xử lý NOBODY ở mục 3.7 |
| who_can_see_story | VARCHAR(20) | NO | FRIENDS_ONLY | EVERYONE / FRIENDS_ONLY / ONLY_ME |
| who_can_see_profile | VARCHAR(20) | NO | EVERYONE | Enum PrivacyVisibility |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð friendships  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **requester_id** | UUID FK→users | NO | – | Người gửi lời mời |
| **addressee_id** | UUID FK→users | NO | – | Người nhận |
| status | SMALLINT | NO | 1 | Enum FriendshipStatus — lưu số cố định gán tay (`PENDING=1, ACCEPTED=2, REJECTED=3, CANCELLED=4`), KHÔNG lưu chuỗi, theo `skills/naming-conventions.md` #3 (đây là enum MỚI lưu DB, không phải trường hợp cũ được miễn áp dụng) — cùng khuôn `otp_codes.purpose`/`OtpPurposeConverter` |
| message | VARCHAR(200) | YES | NULL | Lời nhắn kèm lời mời |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

**Ràng buộc bắt buộc:** UNIQUE index trên cặp không phân biệt thứ tự
`(LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))` — chỉ tồn tại đúng 1
row cho mỗi cặp user, bất kể ai từng là requester/addressee. Hệ quả cho tầng ứng dụng
(`FriendServiceImpl.sendRequest`):
- A và B cùng lúc gửi lời mời cho nhau (race condition) → 1 trong 2 insert bị unique index chặn
  ở tầng DB (`DataIntegrityViolationException`) dù tầng code đã check "chưa có row" trước đó
  (TOCTOU) — bắt exception này, fetch lại row, xử lý y như case dưới. Lưu ý implementation:
  insert nhánh này PHẢI dùng `saveAndFlush()` chứ không phải `save()` — `id` là UUID sinh trong
  bộ nhớ (Hibernate không cần round-trip DB để có ID) nên `save()` hoãn INSERT thật tới lúc
  transaction commit, bắt exception ở `try/catch` sẽ không kích hoạt nếu không ép flush ngay.

**Index bổ sung** (ngoài unique index trên): `(requester_id, addressee_id)` thường (không unique)
— phục vụ riêng cho query `findByUnorderedPair` (chạy ở MỌI lần gửi/accept lời mời) vì query này
lọc `OR` trực tiếp trên 2 cột đó, không dùng được unique index (index đó dùng hàm
`LEAST`/`GREATEST`, chỉ phục vụ được check constraint lúc insert).
- B đã gửi PENDING cho A trước, A gửi lại cho B → KHÔNG insert row mới, update ngược lại chính
  row đó thành `ACCEPTED` (auto-accept).
- Row cũ đang `REJECTED`/`CANCELLED` (bất kể chiều nào), 1 trong 2 người gửi lại → **reuse lại
  chính row đó**, đổi `requester_id`/`addressee_id` theo chiều mới, đưa status về `PENDING`
  (không insert row mới — sẽ vi phạm unique index).

**Endpoint bổ sung** (không có số # riêng trong bảng 3.1 nhưng cần để #20/#21 dùng được):
`GET /friends/requests/incoming`, `GET /friends/requests/outgoing` — liệt kê lời mời đang
PENDING theo 2 chiều nhận/gửi.

**Gửi lời mời kết bạn qua QR** (mở rộng #19, không phải flow riêng): `GET /friends/qr-token`
cấp 1 token ngẫu nhiên (UUID v4) đại diện cho user gọi, lưu Redis
`cache:friend_qr_token:{token} → user_id`, TTL 5 phút (`RedisKeys.FRIEND_QR_TOKEN_TTL_SECONDS`),
KHÔNG single-use — cùng 1 token vẫn resolve được cho tới khi hết TTL, vì QR đứng yên trên màn
hình cho nhiều người quét lần lượt, khác OTP dùng 1 lần. FE tự quyết định encode gì vào ảnh QR
(token trần, hay 1 URL chứa token) — Core Service không quan tâm định dạng QR, chỉ cấp/resolve
token.

**Khuyến nghị encode QR (quyết định của FE, ghi lại đây để khỏi lệch giữa các client)**: encode
1 URL đầy đủ (VD `https://<web-domain>/add-friend?token={token}`), KHÔNG encode token trần —
QR chứa URL mới quét được bằng bất kỳ app quét QR nào (camera hệ thống, app khác), không chỉ
quét được từ đúng màn hình "Quét QR" tự viết trong app/web mình. Trang `/add-friend` (FE tự
route, không phải endpoint của Core Service) đọc `token` từ query string, bắt login nếu chưa
đăng nhập (quay lại trang này sau khi login xong), rồi gọi `POST /friends/requests/qr` với
token đó. Nếu FE (web) làm trước app: vẫn dùng chung 1 route `/add-friend` này, quét bằng camera
trình duyệt (`getUserMedia` + lib decode QR phía client như `jsQR`/`html5-qrcode`) chỉ là 1 cách
khác để lấy được URL đó — không bắt buộc, vì URL cũng mở được trực tiếp bằng camera hệ thống
điện thoại như bình thường.

`POST /friends/requests/qr` nhận `{qrToken, message}`, resolve token ra `addressee_id`
rồi chạy lại NGUYÊN luồng #19 (`sendRequest`) — không có guard/check riêng cho nhánh QR, token
chỉ là cách khác để cung cấp `addresseeId`. Token invalid/hết hạn → `FRIEND_QR_TOKEN_INVALID`
(404), không phân biệt "chưa từng tồn tại" hay "đã hết hạn" trong message trả về.

Mỗi user chỉ có đúng 1 token "active" tại 1 thời điểm, track bằng con trỏ Redis riêng
`cache:friend_qr_active:{user_id} → token`:
- **Revoke sớm**: `DELETE /friends/qr-token` — xoá token active ngay, không cần đợi hết TTL
  5 phút (idempotent, gọi khi không có token active nào cũng trả 200 bình thường).
- **Tạo token mới tự huỷ token cũ**: gọi lại `GET /friends/qr-token` khi đã có token active thì
  token cũ bị xoá ngay (qua con trỏ trên) trước khi token mới được tạo — ảnh QR cũ (chụp màn
  hình, tab cũ còn mở) chết ngay, không phải đợi TTL.
- **Giới hạn số lần dùng** (defense-in-depth, KHÔNG phải single-use): mỗi token tối đa
  `FriendQrTokenService.MAX_USES` = 30 lần gửi request (đếm bằng Redis `INCR` atomic trên
  `cache:friend_qr_token_uses:{token}`, cùng TTL với token — bắt buộc atomic, không tách
  đọc/ghi, tránh race 2 lượt quét cùng lúc đều đọc thấy count cũ và cùng vượt hạn mức). Vượt
  hạn mức → `FRIEND_QR_TOKEN_USAGE_LIMIT_REACHED` (429), token vẫn còn hạn TTL nhưng không
  resolve tiếp được nữa. Mục đích: chặn trường hợp QR bị lộ ra ngoài ý định ban đầu (đăng công
  khai, forward hàng loạt) bị spam request, KHÔNG nhằm hạn chế cách dùng bình thường (show QR
  cho vài người quét trong 1 buổi gặp mặt không bao giờ chạm ngưỡng này).

**#23 Danh sách bạn bè** (`GET /friends`): trả `List<UserResponse>` thuần, KHÔNG có
`PublicPresenceDTO` như mô tả gốc — chưa có client gRPC Presence nào trong codebase (Presence
thuộc Realtime Gateway, không phải Core Service), ghi TODO trong code, nối khi Presence sẵn sàng.

**#26 Danh sách bạn thân (Close Friends)**: 1 chiều (A đánh dấu B là bạn thân không có nghĩa B
đánh dấu A) — đúng bản chất product feature, không cascade ngược. Muốn thêm ai vào close friends
thì 2 người phải đang ACCEPTED trong `friendships` trước (lỗi `FRIENDSHIP_NOT_FOUND` nếu chưa),
add/remove đều idempotent. Route: `POST /friends/close/{userId}`, `DELETE /friends/close/{userId}`,
`GET /friends/close`.

**Block chỉ mute tin nhắn/cuộc gọi, KHÔNG đụng gì tới bạn bè** (#24) — **quyết định lại
2026-08-09**, thay cho thiết kế "full wall" ban đầu (xoá friendship/close-friend, chặn friend
request, ẩn profile — đã lên code rồi revert). Từ giờ:
- Block **không** cascade xoá `friendships`/`close_friends` — 2 người vẫn là bạn/close friend
  bình thường sau khi 1 bên block.
- Gửi lời mời kết bạn (#19), xem profile (#27), tìm kiếm (#18) đều **không** check `user_blocks`
  nữa — hoàn toàn không bị ảnh hưởng bởi block.
- Cả block và unblock vẫn idempotent — gọi lại khi đã ở đúng trạng thái không báo lỗi, trả 200.
- Bảng `user_blocks` có cột `scope` (VARCHAR, lưu thẳng chữ `MESSAGE`/`CALL`/`ALL` — không đánh
  số, khác với `FriendshipStatus`/`OtpPurpose` vì giá trị/nhu cầu tra cứu quá đơn giản không đáng
  thêm converter, giống ngoại lệ đã có sẵn ở `two_factor_methods.method`) nói rõ block đang mute
  CÁI GÌ: `MESSAGE` (chỉ tin nhắn), `CALL` (chỉ cuộc gọi), `ALL` (cả 2). v1 bấm nút "Chặn" luôn
  tạo `ALL`; chọn riêng `MESSAGE` hay `CALL` là tính năng UI làm sau, chưa có ở v1.

**TODO — enforcement chưa code (messaging-service/call-service):** Core Service hiện chỉ là nơi
lưu trữ + nguồn sự thật cho quan hệ block, CHƯA có gì thật sự chặn tin nhắn/cuộc gọi. Cần:
- gRPC `CheckBlock(actorId, targetId)` ở Core Service, trả `blocked: boolean` — Messaging
  Service/Call Service tự gọi trước khi cho gửi tin/bắt đầu gọi. Core Service không chủ động
  can thiệp vào luồng của service khác (đúng nguyên tắc database-per-service).
- Khi triển khai xong, việc còn cần chốt là hướng nào chặn: chặn 1 chiều (A block B → B vẫn nhắn
  được cho A nhưng A không thấy) hay 2 chiều (cả 2 không nhắn được nhau) — CHƯA quyết, ghi TODO
  ở đây để không quên khi tới lượt code call-service/messaging-service.

**#27 Xem profile người khác** (`GET /users/{userId}`, domain mới `profile/`): trả
`USER_NOT_FOUND` (404) nếu user không tồn tại. Check `privacy_settings.who_can_see_profile`
(#16) BỎ QUA — domain Privacy chưa code, mọi profile hiện public hoàn toàn, ghi TODO trong code,
nối khi #16 xong (cùng lý do đã bỏ qua `who_can_add_friend` ở #19).

### **ð user_blocks  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **blocker_id** | UUID FK→users | NO | – | Người chặn |
| **blocked_id** | UUID FK→users | NO | – | Người bị chặn |
| scope | VARCHAR(10) | NO | 'ALL' | `MESSAGE`/`CALL`/`ALL` (duy nhất v1 tạo ra — chặn cả 2) |
| reason | VARCHAR(100) | YES | NULL | Lý do (tuỳ chọn) |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

**Endpoint bổ sung** (không có số # riêng trong bảng 3.1 nhưng cần cho UI "danh sách đã chặn"):
`GET /blocks`.

### **ð close_friends  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **user_id** | UUID FK→users | NO | – | PK composite |
| **friend_id** | UUID FK→users | NO | – | PK composite |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð login_audit_logs  [PostgreSQL – Theo dõi bảo mật]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users | YES | NULL | Null nếu login thất bại (unknown user) |
| event_type | VARCHAR(30) | NO | – | LOGIN_SUCCESS / LOGIN_FAILED / LOGOUT / PASSWORD_CHANGE / SESSION_REVOKE |
| ip_address | INET | YES | NULL | IP client |
| user_agent | TEXT | YES | NULL | Browser/App |
| **device_id** | VARCHAR(100) | YES | NULL | Device fingerprint |
| **session_id** | UUID | YES | NULL | Session liên quan |
| failure_reason | VARCHAR(100) | YES | NULL | WRONG_PASSWORD / ACCOUNT_BLOCKED / OTP_EXPIRED... |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

**Retention:** bảng này (và `group_audit_logs` ở mục 3.9) tăng vô hạn theo traffic — bắt buộc có
job archive định kỳ (VD: cron hàng đêm) chuyển dòng cũ hơn N tháng (chốt cụ thể theo yêu cầu
compliance, gợi ý mặc định 12 tháng) sang cold storage/table riêng rồi xoá khỏi bảng chính, để
tránh bảng phình to làm chậm query và index. Đây là việc vận hành bắt buộc phải làm ở scale lớn,
không phải tối ưu tuỳ chọn.

### **ð outbox_events (identity_db)  [PostgreSQL – Outbox nội bộ]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **event_id** | UUID UNIQUE | NO | gen_random_uuid() | Idempotency key |
| event_type | VARCHAR(100) | NO | – | Tên event, vd: user.registered |
| **aggregate_id** | UUID | NO | – | ID đối tượng (user_id, friendship_id...) |
| aggregate_type | VARCHAR(50) | NO | – | User / Friendship / Block |
| payload | JSONB | NO | – | Nội dung đầy đủ |
| exchange | VARCHAR(100) | NO | – | RabbitMQ exchange |
| routing_key | VARCHAR(100) | NO | – | RabbitMQ routing key |
| status | VARCHAR(20) | NO | PENDING | PENDING / PUBLISHED / FAILED |
| retry_count | INT | NO | 0 | Số lần retry |
| last_error | TEXT | YES | NULL | Lỗi lần cuối |
| created_at | TIMESTAMPTZ | NO | now() | UTC – cùng transaction nghiệp vụ |
| published_at | TIMESTAMPTZ | YES | NULL | UTC – publish thành công |

### **ð received_event_dedup (identity_db)  [PostgreSQL – Chống xử lý trùng]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **event_id** | UUID PK | NO | – | UNIQUE – event đã xử lý |
| event_type | VARCHAR(100) | NO | – | Loại event |
| processed_at | TIMESTAMPTZ | NO | now() | UTC |

## **3.5 RabbitMQ Events – Identity Service publish**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| user.registered | user.exchange | user.registered | Đăng ký thành công | Notification (welcome email) |
| user.profile_updated | user.exchange | user.profile_updated | Cập nhật profile | Search Service (reindex) |
| user.blocked_by_admin | user.exchange | user.blocked | Admin block tài khoản | WS GW (force disconnect) |
| friend.request_sent | user.exchange | friend.request_sent | Gửi lời mời kết bạn | Notification Service |
| friend.accepted | user.exchange | friend.accepted | Chấp nhận kết bạn | Notification, Messaging (unlock DM) |
| friend.removed | user.exchange | friend.removed | Xoá bạn bè | Messaging Service |
| user.block_set | user.exchange | user.block_set | Chặn user | Messaging, Presence Service |
| user.block_removed | user.exchange | user.block_removed | Gỡ chặn | Messaging, Presence Service |
| user.reported | user.exchange | user.reported | User bị report | Notification Service (kênh admin/nội bộ CHỈ, không báo người bị report) |

## **3.6 Cấu trúc thư mục**

identity-service/

├── src/main/java/com/chatapp/identity/

│   ├── IdentityApplication.java

│   ├── config/          # SecurityConfig, RedisConfig, RabbitConfig

│   ├── controller/

│   │   ├── AuthController.java

│   │   ├── ProfileController.java

│   │   └── FriendController.java

│   ├── service/

│   │   ├── AuthService.java

│   │   ├── OAuthService.java

│   │   ├── SessionService.java

│   │   ├── OtpService.java

│   │   ├── TwoFactorService.java

│   │   ├── ProfileService.java

│   │   ├── FriendshipService.java

│   │   └── BlockService.java

│   ├── domain/

│   │   ├── entity/      # UserEntity, FriendshipEntity...

│   │   ├── repository/  # UserRepository, FriendshipRepository...

│   │   └── enums/       # FriendshipStatus, OtpPurpose...

│   ├── dto/

│   │   ├── request/     # CreateUserRequest, LoginRequest...

│   │   └── response/    # PublicUserDTO, SessionDTO...

│   ├── grpc/            # IdentityGrpcService (server)

│   ├── outbox/          # OutboxPublisher, OutboxRelayScheduler

│   └── audit/           # LoginAuditService

├── src/main/resources/

│   ├── application.yml

│   └── db/migration/    # Flyway SQL

└── pom.xml

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Java 25 / Spring Boot 4 |
| **Database** | PostgreSQL |
| **Cache** | Redis (cache:group:*, cache:group:members:*) |
| **gRPC exposed** | GetGroupMembers, CheckGroupRole, GetGroupInfo |

## **3.7 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Tạo nhóm** | Insert `groups` (chưa có `conversation_ref`) → gọi gRPC `MessagingService.CreateConversation(type=GROUP, participant_ids=[creator])` (đồng bộ, cần kết quả ngay vì `conversation_ref` NOT NULL) → nhận về `conversation_id` → update `groups.conversation_ref` → publish `group.member_joined` cho chính creator (role=OWNER). |
| **2** | **Đổi tên, ảnh nhóm** | Admin/Owner update group name, avatar_url. |
| **3** | **Tạo QR Code tham gia** | Generate qr_code_token unique. Client render thành QR image. |
| **4** | **Tạo Invite Link** | Generate invite_link_token unique. Không có thời hạn hết hạn tự động (quyết định 2026-08-14 — bỏ hẳn khỏi scope, xem "Lưu ý khi code" bên dưới); lộ thì rotate lại là đủ. |
| **5** | **Reset QR / Invite link** | Reset (rotate) — thu hồi token cũ và cấp token mới trong 1 lần gọi (`POST .../qr-code/reset`, `POST .../invite-link/reset`), đối xứng cho cả QR và invite link. Không có khái niệm "revoke xoá hẳn, không sinh token mới" (bỏ 2026-08-16). |
| **6** | **Tham gia qua Link/QR** | `GET /groups/preview?token=` xem trước (read-only) → `POST /groups/join` mới thật sự tham gia. Nếu require_approval=false → add ngay. Nếu true → tạo join request. Đã code (2026-08-16), xem "Lưu ý khi code". |
| **7** | **Bật/Tắt chế độ duyệt** | Admin/Owner toggle require_approval. |
| **8** | **Duyệt thành viên** | Admin xem danh sách group_join_requests (`GET .../join-requests`), approve/reject từng người (`POST .../join-requests/{id}/approve`\|`reject`) — CHƯA làm bulk, mỗi lần 1 request. Đã code (2026-08-16). |
| **9** | **Thêm thành viên trực tiếp** | Member thêm bạn bè (nếu tắt duyệt, không thì rơi vào `group_join_requests` với `invited_by`). Admin/Owner thêm bất kỳ, luôn bypass duyệt. Đã code (2026-08-16), xem "Lưu ý khi code". **Phần check `who_can_add_to_group` (gRPC `GetPrivacySettings`) VẪN LÀ TODO** — chưa có gRPC client, `group_invite_pending` chưa tồn tại, mọi target hiện coi như addable. |
| **9b** | ~~Giới hạn chống spam (tạo nhóm)~~ | Bỏ hẳn (quyết định 2026-08-12) — không rate limit số nhóm tạo mới/ngày per user. Cùng hướng với quyết định bỏ rate limit + cooldown cho friend request bên dưới. |
| **10** | **Xoá thành viên** | Admin/Owner kick member. Publish event group.member_removed. |
| **11** | **Phong Admin** | Owner set role=ADMIN cho member. |
| **12** | **Thu hồi Admin** | Owner set role=MEMBER. |
| **13** | **Chuyển nhượng Owner** | Owner chuyển role OWNER cho member khác (owner trở thành admin). |
| **14** | **Rời nhóm** | Member tự rời. Nếu Owner rời → cần chuyển nhượng trước, hoặc tự động chọn ADMIN có `joined_at` sớm nhất làm Owner mới; nếu nhóm không còn ADMIN nào (chỉ toàn MEMBER) → tự động chọn MEMBER có `joined_at` sớm nhất; nếu Owner là thành viên duy nhất còn lại → nhóm chuyển `is_deleted=true` (không thể tồn tại nhóm 0 người). |
| **15** | **Xoá nhóm** | Owner xoá nhóm. Soft delete, notify tất cả members. |
| **16** | **Xem danh sách thành viên** | Phân trang, filter by role. |
| **17** | **@mention cá nhân** | Messaging Service gọi GroupService để validate user_id có trong nhóm. |
| **18** | **@all / @everyone** | Chỉ Admin/Owner dùng. Trigger notify tất cả members. |
| **19** | **Mute thành viên trong call** | Admin/Owner mute mic participant (thông qua Call Service). |
| **20** | **Lịch sử thay đổi nhóm** | Ghi group_audit_logs mỗi khi thay đổi thành viên/quyền. |

## **3.8 Enums**

**Enum: GroupMemberRole**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **OWNER** | Chủ nhóm – quyền cao nhất |
| **ADMIN** | Quản trị viên |
| **MEMBER** | Thành viên thường |

**Enum: JoinRequestStatus**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **PENDING** | Đang chờ duyệt |
| **APPROVED** | Đã duyệt vào nhóm |
| **REJECTED** | Bị từ chối |
| **CANCELLED** | Người dùng tự huỷ |

## **3.9 Database Schema**

### **ð groups  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| conversation_ref | VARCHAR(24) | NO | – | MongoDB ObjectId của conversation |
| name | VARCHAR(100) | NO | – | Tên nhóm |
| avatar_url | VARCHAR(500) | YES | NULL | CDN URL |
| description | TEXT | YES | NULL | Mô tả nhóm |
| invite_link_token | VARCHAR(100) UNIQUE | YES | NULL | Token invite link — không có cột expiry (bỏ TTL, quyết định 2026-08-14) |
| qr_code_token | VARCHAR(100) UNIQUE | YES | NULL | Token QR |
| require_approval | BOOLEAN | NO | false | Bật chế độ duyệt |
| only_admin_can_send | BOOLEAN | NO | false | true = nhóm dạng thông báo/broadcast, chỉ ADMIN/OWNER được gửi tin — Messaging Service check qua gRPC `CheckGroupRole` trước khi cho Member gửi |
| max_members | INT | NO | 500 | Giới hạn thành viên |
| member_count | INT | NO | 0 | Cache số lượng thành viên |
| created_by | UUID FK→users | NO | – | Người tạo |
| is_deleted | BOOLEAN | NO | false | Soft delete |
| deleted_at | TIMESTAMPTZ | YES | NULL | UTC |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð group_members  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **group_id** | UUID FK→groups | NO | – | Nhóm |
| **user_id** | UUID FK→users | NO | – | Thành viên – UNIQUE(group_id, user_id) |
| role | VARCHAR(10) | NO | MEMBER | Enum GroupMemberRole |
| joined_at | TIMESTAMPTZ | NO | now() | UTC |
| added_by | UUID FK→users | YES | NULL | Ai thêm vào |
| left_at | TIMESTAMPTZ | YES | NULL | UTC – rời nhóm |
| is_active | BOOLEAN | NO | true | Còn trong nhóm |

### **ð group_join_requests  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **group_id** | UUID FK→groups | NO | – | Nhóm |
| **user_id** | UUID FK→users | NO | – | Người muốn vào |
| status | VARCHAR(15) | NO | PENDING | Enum JoinRequestStatus |
| reviewed_by | UUID FK→users | YES | NULL | Admin xử lý |
| reviewed_at | TIMESTAMPTZ | YES | NULL | UTC |
| reject_reason | VARCHAR(200) | YES | NULL | Lý do từ chối |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

**group_invite_pending [PostgreSQL]** — khác `group_join_requests` ở chỗ: đây là do **người khác
mời** (bị chặn bởi `who_can_add_to_group` của chính người được mời), không phải tự xin vào.

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **group_id** | UUID FK→groups | NO | – | Nhóm |
| **invited_user_id** | UUID FK→users | NO | – | Người bị mời, cần tự xác nhận |
| **invited_by** | UUID FK→users | NO | – | Người gửi lời mời |
| status | VARCHAR(15) | NO | PENDING | PENDING / ACCEPTED / DECLINED |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| responded_at | TIMESTAMPTZ | YES | NULL | UTC |

Publish `group.invite_pending` (Notification Service push cho `invited_user_id`) lúc tạo; user
accept → giống hệt luồng add thành viên bình thường (insert `group_members`); decline → chỉ
update status, không thông báo lại cho người mời (tránh lộ thông tin "bị từ chối" gây khó xử).

### **ð group_audit_logs  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **group_id** | UUID FK→groups | NO | – | Nhóm |
| **actor_id** | UUID FK→users | NO | – | Người thực hiện |
| action | VARCHAR(50) | NO | – | MEMBER_ADDED / MEMBER_REMOVED / ROLE_CHANGED / NAME_CHANGED / GROUP_DELETED... |
| **target_user_id** | UUID | YES | NULL | User bị tác động |
| old_value | TEXT | YES | NULL | Giá trị cũ |
| new_value | TEXT | YES | NULL | Giá trị mới |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

## **3.10 RabbitMQ Events**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| group.member_joined | group.exchange | group.member_joined | Thành viên vào nhóm | WS GW, Notification |
| group.member_removed | group.exchange | group.member_removed | Kick/rời nhóm | WS GW, Messaging |
| group.role_changed | group.exchange | group.role_changed | Đổi quyền | WS GW → notify |
| group.join_request_new | group.exchange | group.join_request | Yêu cầu vào nhóm | Notification (admin) |
| group.invite_pending | group.exchange | group.invite_pending | Bị thêm vào nhóm nhưng privacy chặn add thẳng (`who_can_add_to_group`) | Notification (người được mời) |
| group.deleted | group.exchange | group.deleted | Xoá nhóm | WS GW, Messaging |

## **3.11 Cấu trúc thư mục**

group-service/

├── src/main/java/com/chatapp/group/

│   ├── controller/

│   │   ├── GroupController.java

│   │   └── JoinRequestController.java

│   ├── service/

│   │   ├── GroupService.java

│   │   ├── MemberService.java

│   │   └── JoinRequestService.java

│   ├── domain/

│   │   ├── entity/  # GroupEntity, GroupMemberEntity

│   │   ├── repository/

│   │   └── enums/   # GroupMemberRole, JoinRequestStatus

│   ├── dto/

│   ├── grpc/        # GroupGrpcService (server)

│   └── outbox/

└── pom.xml

## **3.12 Biệt danh theo nhóm (nickname)**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| group_id | UUID FK→groups | NO | – | PK composite |
| user_id | UUID FK→users | NO | – | PK composite — người được đặt nickname |
| nickname | VARCHAR(50) | NO | – | Tên gọi riêng trong nhóm |
| set_by | UUID FK→users | NO | – | Ai đặt — chính chủ hoặc Admin/Owner đặt cho người khác |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

Bảng: group_member_nicknames [PostgreSQL]. Ràng buộc nghiệp vụ: Member chỉ sửa nickname của chính mình; Admin/Owner sửa được nickname của bất kỳ ai trong nhóm (dùng lại CheckGroupRole đã có ở gRPC Catalog).

## **3.13 File / Folder chung của nhóm — ĐÃ CHUYỂN sang Messaging Service**

> **Đổi kiến trúc (review sau này)**: mục này ban đầu đặt `group_file_folders`/`group_files` tại
> Core Service, FK thẳng vào bảng `groups` (PostgreSQL) của chính Core. Vấn đề: bảng `groups` chỉ
> tồn tại cho chat NHÓM — chat 1-1 (DIRECT) và "cloud cá nhân" (SELF) không hề có row `groups`
> tương ứng (2 loại này chỉ tồn tại trong `conversations` bên Messaging Service), nên không thể
> mở rộng tính năng folder cho DIRECT/SELF nếu tiếp tục FK vào `groups` ở đây.
>
> Vì bản chất tính năng là **tổ chức file theo conversation** (khái niệm đã được Messaging Service
> unify cho cả DIRECT/GROUP/SELF qua `conversations.type`), file/folder nên do Messaging Service sở
> hữu, không phải Core. Đã chuyển toàn bộ sang `04-messaging-service.md` mục 4.13
> (`conversation_folders`/`conversation_files`), áp dụng chung cho cả 3 loại conversation.
> Core Service không còn giữ khái niệm file/folder nữa — quyền Admin/Owner tạo folder khi
> `type=GROUP` vẫn do Core trả lời qua gRPC `CheckGroupRole` như cũ, chỉ có nơi LƯU folder là
> chuyển đi.

## **3.14 Sự kiện / lịch hẹn nhóm**

Bảng: group_events [PostgreSQL]

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| id | UUID | NO | gen_random_uuid() | PK |
| group_id | UUID FK→groups | NO | – | – |
| title | VARCHAR(200) | NO | – | – |
| location | VARCHAR(300) | YES | NULL | – |
| event_time | TIMESTAMPTZ | NO | – | Lưu UTC |
| event_timezone | VARCHAR(50) | NO | – | Tên IANA timezone gốc lúc tạo, VD Asia/Ho_Chi_Minh |
| created_by | UUID FK→users | NO | – | Chỉ Admin/Owner tạo được |
| reminder_minutes_before | INT | NO | 30 | Nhắc trước bao lâu |
| is_cancelled | BOOLEAN | NO | false | – |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

Bảng: group_event_rsvp [PostgreSQL]

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| event_id | UUID FK→group_events | NO | – | PK composite |
| user_id | UUID FK→users | NO | – | PK composite |
| status | VARCHAR(15) | NO | – | GOING / NOT_GOING / MAYBE |
| responded_at | TIMESTAMPTZ | NO | now() | UTC |

Sự kiện RabbitMQ mới: group.event_created, group.event_reminder (publish trước reminder_minutes_before) → Notification Service consume và push. Ghi chú: chưa hỗ trợ event lặp lại — nếu làm sau, phải lưu recurrence rule + timezone gốc, không chốt cứng danh sách UTC vì DST sẽ làm lệch giờ qua các lần lặp.

## **3.14a Ghi chú triển khai entity domain group (bổ sung 2026-08-10)**

Khi code entity JPA cho domain `group/` (branch `feature/group-management`), có vài điểm lệch/thêm
so với mục 3.7–3.14 phía trên — ghi lại đây để code không lệch khỏi doc, thay vì để rải rác trong
Javadoc từng entity:

- **`GroupMemberRole`, `GroupJoinRequestStatus`, `GroupEventRsvpStatus` lưu DB dạng chuỗi
  (`@Enumerated(EnumType.STRING)`)**, không theo pattern số cố định + converter ở
  `skills/naming-conventions.md` #3 — quyết định giống `user_blocks.scope` (`BlockScope`): chỉ
  vài giá trị cố định, không đáng thêm converter.
- **`group_join_requests` có thêm cột `invited_by` (nullable)**, không có trong bảng ở mục 3.9 —
  dùng để phân biệt 2 tình huống cùng rơi vào bảng này: `NULL` = chính `user_id` tự xin vào (qua
  QR/link lúc `require_approval=true`); có giá trị = 1 Member thường (không phải Admin/Owner) chủ
  động đề xuất add `user_id` vào lúc đang bật duyệt, nhưng không add thẳng được.
- **`group_events` có thêm cột `reminded_at`**, không có trong bảng ở mục 3.14 — bắt buộc theo
  root CLAUDE.md "Lưu ý khi code" #3 (chống scheduler publish `group.event_reminder` trùng lặp).
- **Bảng mới `group_admin_permissions`** (không có trong spec gốc) — cho phép Owner giới hạn
  quyền cụ thể của từng Admin (`can_approve_members`, `can_kick_members`, `can_edit_group_info`,
  `can_manage_events`), thay vì Admin mặc định full quyền như Owner. PK composite
  `(group_id, user_id)`, chỉ tồn tại khi role=ADMIN — Owner không có row, luôn full quyền. Tạo với
  toàn bộ cờ `true` ngay lúc promote lên Admin (giữ hành vi cũ làm baseline), xoá hẳn khi
  demote/transfer/kick. 4 quyền này CHỦ Ý không gồm: chuyển nhượng Owner, xoá nhóm, kick
  Owner/Admin khác — luôn chỉ Owner làm được, không có cấu hình nào mở khoá cho Admin.
- **Bỏ hẳn `group_invite_pending`** khỏi scope hiện tại (bảng này lẽ ra dùng khi
  `who_can_add_to_group=NOBODY` chặn add thẳng) — vì domain `privacy/` (nơi lưu
  `who_can_add_to_group`) chưa có 1 dòng code nào trong `core-service`. Quyết định: tạm coi như
  không ai bị chặn privacy khi add vào nhóm, tới khi domain `privacy/` được xây thì làm lại phần
  này.
- **`groups.conversation_ref` tạm nullable** ở entity, khác NOT NULL trong bảng ở mục 3.9 — vì
  chưa có gRPC client gọi `MessagingService.CreateConversation` trong codebase, cột này sẽ được
  set bằng 1 UPDATE ngay sau khi gRPC đó được nối, không set được lúc insert.
- **`GroupMemberEntity` vẫn dùng `id` UUID riêng** (không composite `(group_id, user_id)` như
  `group_member_nicknames`/`group_event_rsvp`/`group_admin_permissions`) — quyết định giữ nguyên
  dù về bản chất cặp này có thể làm PK (không có tình huống đổi vai như `requester_id`/
  `addressee_id` ở `friendships`); có thể đổi sang composite key sau nếu cần.
- **#2 (đổi tên/ảnh/description) — phần `avatar_url` tạm chưa code** (2026-08-12): chỉ
  `PATCH /groups/{groupId}` đổi `name`/`description` là chạy được ngay, đổi `avatar_url` để lại
  TODO trong `GroupServiceImpl.updateInfo()` tới khi codebase có gRPC/REST client gọi Media
  Service — client phải tự upload qua Media Service lấy CDN URL trước rồi mới gọi endpoint này,
  Core không tự tải ảnh (nguyên tắc #8 root CLAUDE.md, giống quyết định OAuth avatar ở "Lưu ý khi
  code" #15).
- **#3-5 (QR code / invite link / reset) dùng chung 1 permission gate** `CAN_EDIT_GROUP_INFO` qua
  `GroupPermissionResolver` (`group/util/`, điểm check quyền duy nhất cho mọi service trong
  domain `group/`) — token sinh bằng `UUID.randomUUID().toString()` (giống pattern
  `FriendQrTokenService`), ghi đè trực tiếp token cũ (không soft-invalidate).
- **Bỏ khái niệm "revoke" (xoá hẳn, không sinh token mới)** — quyết định 2026-08-16: không có
  nhu cầu sản phẩm thật sự cho trạng thái "tắt hẳn QR/invite link", chỉ có nhu cầu "tôi sợ QR/link
  bị lộ, đổi ngay cái mới". `DELETE /groups/{groupId}/qr-code` và
  `DELETE /groups/{groupId}/invite-link` (null hoá token) đã bị xoá, thay bằng
  `POST /groups/{groupId}/qr-code/reset` và `POST /groups/{groupId}/invite-link/reset` — thu hồi
  token cũ VÀ cấp token mới trong đúng 1 lần gọi (`GroupServiceImpl.resetQrCode`/
  `resetInviteLink`, rotate ngay cả khi trước đó chưa có token nào active), trả về token mới
  trong response body (`GroupQrCodeResponse`/`GroupInviteLinkResponse`) — không còn `204 No
  Content` như revoke cũ. `POST .../qr-code`/`POST .../invite-link` (không có hậu tố `/reset`)
  vẫn giữ nguyên, hành vi thực chất giống hệt reset (cũng rotate vô điều kiện) — tách riêng 2
  route chỉ để FE có 2 nút ngữ nghĩa rõ ràng ("Tạo QR" lần đầu vs "Reset" khi lo bị lộ), không
  phải 2 luồng nghiệp vụ khác nhau.
- **Bỏ hẳn TTL của invite link** (`invite_link_expires_at`, quyết định 2026-08-14) — spec cũ #4
  cho phép đặt thời hạn hết hạn, nhưng rotate (reset) đã tự đủ để vô hiệu token bị lộ ngay lập
  tức, không cần thêm cơ chế tự hết hạn theo thời gian; giữ TTL chỉ thêm phức tạp không tương ứng
  với lợi ích thực tế. `GenerateInviteLinkRequest` (DTO nhận `expiresInMinutes`) bị xoá,
  `POST /groups/{groupId}/invite-link` không nhận body nữa — cùng dạng với `POST .../qr-code`.
- **#6 (join qua QR/link), #8 (duyệt thành viên), #9 (thêm trực tiếp) đã code (2026-08-16)** —
  package mới `group/service/`: `GroupJoinServiceImpl` (preview/join/list/approve/reject) +
  `GroupMemberServiceImpl` (add trực tiếp), controller `GroupJoinController`/
  `GroupMemberController`. Chi tiết:
  - **`GET /groups/preview?token=xxx`** — resolve QR/invite-link token (thử `qr_code_token`
    trước, fallback `invite_link_token`) ra `{id, name, avatarUrl, memberCount,
    requireApproval}`, **read-only, không có side-effect** — an toàn gọi mỗi lần trang FE load
    trước khi user bấm "Tham gia". Token không hợp lệ/nhóm đã xoá → `GROUP_INVITE_INVALID` (404).
  - **`POST /groups/join {token}`** — mới THẬT SỰ join. `require_approval=false` → add thẳng
    (`GroupJoinOutcome.JOINED`); `true` → insert `group_join_requests` PENDING
    (`GroupJoinOutcome.PENDING_APPROVAL`), publish `group.join_request`. Đã là member active →
    `GROUP_ALREADY_MEMBER` (409). Full (`member_count >= max_members`) → `GROUP_MEMBER_LIMIT_
    REACHED` (409) — check này nằm trong `GroupMembershipMutator.addMemberDirectly` (điểm DUY
    NHẤT thêm row `group_members`/tăng `member_count`/publish `group.member_joined`, dùng chung
    bởi join-by-token, approve, VÀ add trực tiếp — không path nào tự ý thêm member ngoài đây).
  - **Chống spam duplicate request**: `GroupMembershipMutator.createOrReuseJoinRequest` check
    tồn tại request PENDING của đúng `(group_id, user_id)` trước khi insert — quét lại cùng QR
    nhiều lần trong lúc chờ duyệt KHÔNG tạo thêm row mới (không publish `group.join_request`
    thêm lần nữa), khớp unique index `idx_group_join_requests_pending` đã có sẵn ở migration
    `V20260812100000`.
  - **`GET /groups/{groupId}/join-requests`**, **`POST .../join-requests/{requestId}/approve`**,
    **`POST .../join-requests/{requestId}/reject {reason?}`** — cần `CAN_APPROVE_MEMBERS` qua
    `GroupPermissionResolver`. Request không tồn tại HOẶC đã review rồi (không còn PENDING) đều
    trả `GROUP_JOIN_REQUEST_NOT_FOUND` (404) — không phân biệt 2 trường hợp trong response,
    giống cách `FRIEND_QR_TOKEN_INVALID` không phân biệt "chưa từng có" hay "hết hạn".
  - **`POST /groups/{groupId}/members {userId}`** (`GroupMemberServiceImpl.addMember`) — actor
    phải đang active trong nhóm. **Admin/Owner luôn bypass `require_approval`** (add thẳng luôn,
    vì chính họ là người duyệt); **Member thường** add thẳng được CHỈ KHI `require_approval=
    false`, còn lại rơi vào `group_join_requests` với `invited_by=actorId` (phân biệt với tự
    xin vào qua token, `invited_by=NULL`) — dùng lại đúng
    `GroupMembershipMutator.createOrReuseJoinRequest`, publish `group.join_request` y hệt nhánh
    join-by-token. Target đã active → `GROUP_ALREADY_MEMBER`; target không tồn tại →
    `USER_NOT_FOUND`.
  - **CHƯA làm — TODO privacy check `who_can_add_to_group`** (gRPC `GetPrivacySettings`, nhánh
    NOBODY → `group_invite_pending`, spec #9 nguyên bản): client gRPC đó chưa tồn tại trong
    codebase (giống TODO đã có sẵn ở `GroupServiceImpl#createGroup`), nên MỌI target hiện tại
    đều coi như addable — `group_invite_pending` (bảng riêng, khác `group_join_requests`) CHƯA
    được tạo, entity CHƯA tồn tại. Nối lại nhánh này khi có gRPC client thật.
  - **Response DTO dùng chung 1 enum** `GroupJoinOutcome` (`JOINED`/`PENDING_APPROVAL`) cho cả
    `JoinGroupResponse` (join qua token) và `AddMemberResponse` (add trực tiếp) — tránh 2 chuỗi
    "magic string" lệch nhau giữa 2 luồng cùng ý nghĩa.
- **#10 (kick), #11 (phong Admin), #12 (thu hồi Admin) đã code (2026-08-17)** — thêm vào
  `GroupMemberServiceImpl`/`GroupMemberController`. Lúc code 3 hàm này `GroupLockService` (advisory
  lock theo `group_id`) chưa tồn tại — được viết ngay sau đó cho #13 (xem bên dưới), nhưng CHƯA
  retrofit ngược lại cho 3 hàm này lẫn `addMember`/`joinByToken`/`approveJoinRequest`:
  - **`DELETE /groups/{groupId}/members/{userId}`** (`kickMember`) — actor cần `CAN_KICK_MEMBERS`
    qua `GroupPermissionResolver`. Rule cố định (không nằm trong `group_admin_permissions`, không
    ai cấu hình được): **không kick được Owner**, **không kick được Admin khác trừ khi actor là
    Owner** — cả 2 tự nhiên chặn luôn self-kick (Owner/Admin kick chính mình bị chặn bởi đúng 2
    rule này, MEMBER thì không có `CAN_KICK_MEMBERS` nên bị chặn từ bước permission) →
    `GROUP_CANNOT_KICK_OWNER_OR_ADMIN` (403). Set `is_active=false, left_at=now()`, giảm
    `groups.member_count`, xoá row `group_admin_permissions` nếu target là Admin, publish
    `group.member_removed` (`GroupMemberRemovedMessage{userId, removedBy}`). Target không active
    trong nhóm → `GROUP_TARGET_NOT_A_MEMBER` (404, tách riêng khỏi `GROUP_NOT_A_MEMBER` vì đó nói
    về actor, không phải target).
  - **`POST /groups/{groupId}/admins/{userId}`** (`promoteToAdmin`) — CHỈ Owner (fixed rule, đi
    qua `GroupPermissionResolver#requireOwner` mới thêm, không phải 1 cờ trong
    `group_admin_permissions`). Target phải đang là MEMBER active, đã là Admin/Owner →
    `GROUP_TARGET_ALREADY_ADMIN` (409). Set role=ADMIN, insert `group_admin_permissions` full-true
    default (constructor có sẵn), publish `group.role_changed`
    (`GroupRoleChangedMessage{userId, newRole, changedBy}`).
  - **`DELETE /groups/{groupId}/admins/{userId}`** (`demoteToMember`) — CHỈ Owner. Target phải
    đang là ADMIN, không phải → `GROUP_TARGET_NOT_AN_ADMIN` (404). Set role=MEMBER, xoá row
    `group_admin_permissions`, publish `group.role_changed`.
- **#13 (chuyển Owner) đã code (2026-08-17)** — `GroupMemberServiceImpl#transferOwnership`,
  `PUT /groups/{groupId}/owner {userId}`. CHỈ Owner (`requireOwner`). Không tự chuyển cho chính
  mình → `GROUP_SELF_TRANSFER_NOT_ALLOWED` (400). Target phải đang active trong nhóm →
  `GROUP_TARGET_NOT_A_MEMBER` (404, dùng lại code #10-12). Actor cũ → role=ADMIN + insert
  `group_admin_permissions` full-true (baseline như promote thường); target → role=OWNER + xoá
  row `group_admin_permissions` nếu có. Publish `group.role_changed` 2 lần (1 cho actor cũ, 1 cho
  Owner mới).
  - **`GroupLockService` mới** (`group/util/`) — advisory lock Postgres theo `group_id`, dùng
    **non-blocking** (`pg_try_advisory_xact_lock`, KHÔNG phải bản blocking
    `pg_advisory_xact_lock` như `PairLockService` cũ của friend/block) — 2 tx cùng sửa 1 nhóm thì
    tx thứ 2 fail ngay với `GROUP_CONCURRENT_MODIFICATION` (409) thay vì chờ, tránh phải viết
    retry loop kiểu optimistic lock cho case tần suất va chạm thấp như nhóm chat. Gọi
    `groupLockService.tryLock(groupId)` làm việc ĐẦU TIÊN trong `transferOwnership`, trước mọi
    `SELECT` — chặn đúng write-skew "2 request transferOwner cùng lúc từ 1 Owner sang 2 người
    khác nhau đều đọc thấy actor còn là OWNER trước khi tx nào commit, cả 2 cùng pass check, cả 2
    cùng ghi → nhóm có 2 OWNER" (row-lock của `UPDATE` không cứu được vì 2 tx ghi vào 2 row khác
    nhau, không đụng nhau).
  - **CHƯA retrofit lock cho #10-12 và #6/8/9** — `kickMember`/`promoteToAdmin`/`demoteToMember`/
    `addMember`/`joinByToken`/`approveJoinRequest` vẫn chưa gọi `GroupLockService`, chỉ
    `transferOwnership` có. Nối lại khi cần (rủi ro thấp hơn #13 vì không có invariant "chỉ 1
    OWNER" toàn nhóm phải giữ, nhưng `member_count`/role vẫn có thể lệch nếu 2 mutation chồng
    nhau).

## **3.15 Last seen bền vững**

Thêm field last_seen_at TIMESTAMPTZ vào bảng users (mục 3.4). Cơ chế cập nhật: Realtime Gateway publish presence.offline khi user disconnect → Core Service consume event này → update users.last_seen_at = now(). Không ghi DB đồng bộ mỗi lần connect/disconnect, đi qua event để tự throttle.

## **3.16 Report người dùng (Trust & Safety)**

> **Phạm vi**: Core Service CHỈ xử lý report ở cấp độ **user profile** (đúng nguyên tắc
> database-per-service — dữ liệu report gắn với domain nào thì service đó tự quản). Report
> tin nhắn cụ thể là việc của Messaging Service (bảng report riêng trong MongoDB của họ), report
> bài viết/story là việc của Social Service — KHÔNG dồn hết report vào Core. Core chỉ là nơi
> report "người dùng này nói chung" (fake account, harassment lặp lại nhiều nơi...).

Bảng: `reports` [PostgreSQL]

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **reporter_id** | UUID FK→users | NO | – | Người báo cáo |
| **reported_user_id** | UUID FK→users | NO | – | Người bị báo cáo |
| reason | VARCHAR(30) | NO | – | Enum ReportReason |
| description | TEXT | YES | NULL | Bắt buộc nếu reason=OTHER |
| status | VARCHAR(20) | NO | PENDING | Enum ReportStatus |
| reviewed_by | UUID FK→users | YES | NULL | Admin xử lý |
| reviewed_at | TIMESTAMPTZ | YES | NULL | UTC |
| resolution_note | TEXT | YES | NULL | Ghi chú kết quả xử lý |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

**Quy tắc:**
- Rate limit report gửi đi (dùng chung cơ chế `cache:rate_limit:*` ở mục 3.7 #9b, key
  `cache:rate_limit:report:{user_id}`) — chống lạm dụng report hàng loạt để hại người khác.
- 1 reporter chỉ được có 1 report đang ở trạng thái PENDING/REVIEWING cho cùng 1
  `reported_user_id` tại 1 thời điểm — UNIQUE `(reporter_id, reported_user_id)` khi
  `status IN (PENDING, REVIEWING)` (partial unique index).
- Publish `user.reported` (`user.exchange`) — Notification Service consume để đẩy vào kênh
  **admin/nội bộ**, KHÔNG bao giờ thông báo cho `reported_user_id` là họ vừa bị report (tránh
  lộ danh tính reporter, tránh trả thù).
- Report không tự động block hay ẩn nội dung gì — chỉ tạo record chờ admin xem xét thủ công
  (hoặc hệ thống tự động hoá sau này nếu 1 user bị report vượt ngưỡng trong khoảng thời gian
  ngắn, nhưng đó là tính năng để version sau, không phải MVP).
