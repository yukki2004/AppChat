# **SERVICE 3 – CORE SERVICE**

Đảm nhận: Đăng ký, Đăng nhập, OAuth2, 2FA, Cookie Session, Block login, Quản lý thiết bị, Profile cơ bản, Privacy settings, Kết bạn, Chặn. Gộp User + Friend thành 1 service vì tất cả đều xoay quanh identity người dùng.

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Java 21 / Spring Boot 3 |
| **Database** | PostgreSQL |
| **Cache** | Redis (session, OTP, rate limit login) |
| **Auth** | HttpOnly Secure SameSite=Strict Cookie. access_token = JWT RS256 (TTL 15p, verify tại chỗ ở Gateway). refresh_token = UUID v4 ngẫu nhiên, lưu Redis+DB TTL rolling 30 ngày. Chi tiết: `system/05-cookie-auth-flow.md`. |
| **gRPC exposed** | RefreshAccessToken, GetUserPublicInfo, CheckFriendship, CheckBlock, GetPrivacySettings |
| **Outbox** | Bảng outbox_events nội bộ, Go relay worker đọc và publish RabbitMQ |

## **3.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Đăng ký bằng Email** | Nhập email + password → gửi OTP email → xác minh → tạo user + session cookie. |
| **2** | **Đăng ký bằng SĐT** | Nhập phone → gửi OTP SMS → xác minh → tạo user. |
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
| **18** | **Tìm kiếm user** | Full-text search display_name + exact match username. Trả PublicUserDTO, ẩn blocked users. |
| **19** | **Gửi lời mời kết bạn** | Tạo friendship (status=PENDING). Publish event friend.request_sent. |
| **20** | **Chấp nhận kết bạn** | Update friendship status=ACCEPTED. Publish event friend.accepted. |
| **21** | **Từ chối / Huỷ lời mời** | Update status=REJECTED / CANCELLED. |
| **22** | **Xoá bạn bè** | Delete friendship record. Publish event friend.removed. |
| **23** | **Danh sách bạn bè** | Trả danh sách kèm PublicPresenceDTO (gRPC Presence). |
| **24** | **Chặn người dùng** | Insert user_blocks. Publish event user.blocked. Ẩn khỏi search, danh sách online. |
| **25** | **Gỡ chặn** | Delete user_blocks. Publish event user.unblocked. |
| **26** | **Danh sách bạn thân (Close Friends)** | Quản lý close_friends list – dùng cho story visibility. |
| **27** | **Xem profile người khác** | Trả thông tin theo privacy_settings. Blocked → 404. |
| **28** | **Backup codes 2FA** | Generate 8 backup codes, lưu hash. Dùng thay TOTP khi mất thiết bị. |
| **29** | **Audit Log truy cập** | Ghi mọi login, logout, đổi password vào login_audit_logs. |
| **30** | **Report người dùng** | Tạo `reports` (reported_type=USER), rate-limit số report gửi/ngày để chống lạm dụng, publish `user.reported` CHỈ cho kênh admin (không báo cho người bị report — tránh trả thù). Xem mục 3.16. |

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
| **LOGIN_2FA** | Xác thực 2 bước khi đăng nhập |
| **RESET_PASSWORD** | Đặt lại mật khẩu |
| **CHANGE_EMAIL** | Đổi email |

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
| **user_id** | UUID FK→users | NO | – | Người dùng |
| provider | VARCHAR(20) | NO | – | GOOGLE / FACEBOOK / APPLE |
| **provider_user_id** | VARCHAR(255) | NO | – | ID từ provider, UNIQUE per provider |
| provider_email | VARCHAR(255) | YES | NULL | Email từ provider |
| access_token_enc | TEXT | YES | NULL | Encrypted access token |
| refresh_token_enc | TEXT | YES | NULL | Encrypted refresh token |
| token_expires_at | TIMESTAMPTZ | YES | NULL | Hết hạn access token (UTC) |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

**Quy tắc account linking (bắt buộc, chống account-takeover):** khi OAuth callback trả về email
trùng với 1 user đã tồn tại (đăng ký bằng email/password hoặc provider khác), CHỈ tự động link
vào `user_id` đó nếu provider xác nhận `email_verified = true` trong response của họ (Google/
Facebook đều có field này; Apple luôn coi là verified). Nếu provider không xác nhận email đã
verify, hoặc không trả field đó, KHÔNG được tự động link — phải bắt user xác minh thêm 1 bước
(gửi OTP về email đó, xác nhận đúng chủ sở hữu) trước khi gộp 2 tài khoản.

### **ð user_sessions  [PostgreSQL (index Redis primary)] — lưu refresh_token, KHÔNG phải access_token**

> access_token (JWT) không lưu server-side (self-contained, verify bằng chữ ký). Bảng này lưu
> refresh_token — xem `system/05-cookie-auth-flow.md`.
>
> **Không lưu refresh_token dạng plaintext** — cookie giữ giá trị gốc (UUID v4), DB/Redis chỉ
> lưu `SHA-256(refresh_token)` làm khoá tra cứu, giống cách `otp_codes.code_hash` và
> `two_factor_auth.backup_codes_hash` đã làm. Verify bằng cách hash lại giá trị nhận từ cookie
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
| ip_address | INET | YES | NULL | IP đăng nhập |
| user_agent | TEXT | YES | NULL | Browser / app user agent |
| is_active | BOOLEAN | NO | true | Session còn hiệu lực |
| expires_at | TIMESTAMPTZ | NO | – | UTC – hết hạn (now + 30d) |
| last_active_at | TIMESTAMPTZ | NO | now() | UTC – hoạt động cuối |
| revoked_at | TIMESTAMPTZ | YES | NULL | UTC – bị thu hồi |
| revoke_reason | VARCHAR(50) | YES | NULL | logout / admin_block / password_change / all_logout |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð otp_codes  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| target | VARCHAR(255) | NO | – | Email hoặc phone nhận OTP |
| code_hash | VARCHAR(255) | NO | – | SHA-256 hash của mã 6 số |
| purpose | VARCHAR(30) | NO | – | Enum OtpPurpose |
| **user_id** | UUID FK→users | YES | NULL | Null nếu chưa tạo account |
| attempt_count | INT | NO | 0 | Số lần nhập sai |
| max_attempts | INT | NO | 5 | Giới hạn thử |
| expires_at | TIMESTAMPTZ | NO | – | UTC (now + 5 phút) |
| used_at | TIMESTAMPTZ | YES | NULL | UTC – đã sử dụng |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð two_factor_auth  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **user_id** | UUID PK FK→users | NO | – | 1-1 với users |
| is_enabled | BOOLEAN | NO | false | Bật 2FA |
| method | VARCHAR(10) | YES | NULL | TOTP / SMS / EMAIL |
| totp_secret_enc | VARCHAR(255) | YES | NULL | Encrypted TOTP secret (AES-256) |
| backup_codes_hash | TEXT[] | YES | NULL | Mảng SHA-256 hash của 8 backup codes |
| backup_codes_used | INT | NO | 0 | Số backup code đã dùng |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

> **Lazy-insert, không tạo sẵn dòng lúc register.** Đại đa số user không bao giờ bật 2FA, nên
> KHÔNG insert dòng `two_factor_auth` mặc định cho mọi user lúc tạo tài khoản — chỉ
> `INSERT ... ON CONFLICT (user_id) DO UPDATE` (upsert) khi user bấm bật/tắt/đổi method 2FA lần
> đầu. Ở bước login, `SELECT ... WHERE user_id = ?` không trả dòng nào ⇒ coi như
> `is_enabled = false`, bỏ qua bước 2FA, phát access_token/refresh_token thật luôn — KHÔNG được
> coi "không có dòng" là lỗi hay tự động insert dòng mới ở luồng login.

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
| status | VARCHAR(20) | NO | PENDING | Enum FriendshipStatus |
| message | VARCHAR(200) | YES | NULL | Lời nhắn kèm lời mời |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

**Ràng buộc bắt buộc:** UNIQUE index trên cặp không phân biệt thứ tự
`(LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))` — chống race
condition khi A và B cùng lúc gửi lời mời kết bạn cho nhau, tạo ra 2 dòng PENDING song song
(A→B và B→A) thay vì phát hiện lời mời ngược chiều đã tồn tại và tự động chuyển thành ACCEPTED.

### **ð user_blocks  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **blocker_id** | UUID FK→users | NO | – | Người chặn |
| **blocked_id** | UUID FK→users | NO | – | Người bị chặn |
| reason | VARCHAR(100) | YES | NULL | Lý do (tuỳ chọn) |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

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
| **Ngôn ngữ** | Java 21 / Spring Boot 3 |
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
| **4** | **Tạo Invite Link** | Generate invite_link token, có thể đặt thời hạn hết hạn. |
| **5** | **Làm mới / Xoá invite link** | Reset invite_link_token. Link cũ vô hiệu. |
| **6** | **Tham gia qua Link/QR** | Nếu require_approval=false → add ngay. Nếu true → tạo join request. |
| **7** | **Bật/Tắt chế độ duyệt** | Admin/Owner toggle require_approval. |
| **8** | **Duyệt thành viên** | Admin xem danh sách group_join_requests, approve/reject từng người hoặc bulk. |
| **9** | **Thêm thành viên trực tiếp** | Member thêm bạn bè (nếu tắt duyệt). Admin thêm bất kỳ. TRƯỚC KHI add phải check `who_can_add_to_group` của người được thêm (gRPC `GetPrivacySettings`): EVERYONE/FRIENDS_ONLY → add thẳng như bình thường (vẫn phải thoả điều kiện quan hệ tương ứng); NOBODY → không add thẳng được, tạo `group_invite_pending` (trạng thái chờ người đó tự xác nhận muốn vào) thay vì thêm ngay, publish `group.invite_pending` để Notification báo cho người được mời. |
| **9b** | **Giới hạn chống spam** | Rate limit số lời mời kết bạn gửi đi / ngày (gợi ý 50/ngày) và số nhóm tạo mới / ngày per user (gợi ý 10/ngày) — Redis `INCR` + `EXPIRE 86400` theo key `cache:rate_limit:friend_request:{user_id}` / `cache:rate_limit:group_create:{user_id}`, vượt ngưỡng trả lỗi `RATE_LIMIT_EXCEEDED`. Bị B từ chối lời mời kết bạn → set `cache:friend_request_cooldown:{requester_id}:{addressee_id}` TTL 24h, A không được gửi lại cho đúng B trong thời gian này — chống harassment lặp lại. |
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
| invite_link_token | VARCHAR(100) UNIQUE | YES | NULL | Token invite link |
| invite_link_expires_at | TIMESTAMPTZ | YES | NULL | Hết hạn link (UTC) |
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
