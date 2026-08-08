# **PHỤ LỤC F – LUỒNG NGHIỆP VỤ & SỰ PHỤ THUỘC GIỮA CÁC SERVICE**

*Quy ước ký hiệu giao thức dùng trong toàn phụ lục: REST = HTTP qua API Gateway · gRPC = gọi đồng bộ nội bộ giữa service · RabbitMQ = publish/consume bất đồng bộ · WS = push qua Realtime Gateway · R2 = client thao tác thẳng với Cloudflare R2, không qua backend.*

### **F.0 Bảng phụ thuộc tổng quan giữa các service**

|  |  |  |  |
| :-: | :-: | :-: | :-: |
| **Service** | **gRPC gọi ra service khác** | **RabbitMQ publish (exchange chính)** | **RabbitMQ consume từ** |
| API Gateway | Core (`RefreshAccessToken`, chỉ khi `/auth/refresh` — request thường verify JWT tại chỗ, không gRPC) | – | – |
| Realtime Gateway | Verify JWT tại chỗ khi WS handshake, không gRPC | presence.exchange | user, chat, group, notification, call, social, media exchange (để fan-out) |
| Core Service | – | user.exchange | presence.exchange (cập nhật last_seen), media.exchange (dọn rác group_files) |
| Messaging Service | Core (CheckFriendship, CheckBlock), Group module trong Core (GetGroupMembers) | chat.exchange | media.exchange (media.upload_completed để cập nhật media_url) |
| Notification Service | – | notification.exchange | user, chat, group, social, call, media exchange (gần như tất cả) |
| Media Service | – | media.exchange | – |
| Call Service | Notification (SendVoIPPush) | call.exchange | – |
| Social Service | Core (CheckFriendship, CheckBlock) | social.exchange | media.exchange (media.upload_completed) |

*Nguyên tắc chọn giao thức: gRPC khi cần kết quả ngay để quyết định tiếp bước xử lý (VD: check block trước khi cho gửi tin, refresh access token). RabbitMQ khi là side-effect không cần chờ (VD: gửi notification, cập nhật presence, fan-out WS). Không service nào JOIN thẳng DB của service khác — luôn qua gRPC hoặc event. Xác thực access_token (JWT) mỗi request KHÔNG dùng gRPC — verify chữ ký tại chỗ, xem `05-cookie-auth-flow.md`.*

### **F.1 Đăng nhập bằng Email/Số điện thoại + mật khẩu**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway | POST /auth/login {email, password} | REST |   |
| 2 | API Gateway → Core Service | Forward request nguyên vẹn (chưa có token để verify) | REST | Route /auth/* không cần access_token |
| 3 | Core Service | Verify password hash (BCrypt), tạo refresh_token (UUID v4) + access_token (JWT, RS256, exp 15p) | – | Insert user_sessions (refresh_token, PostgreSQL) + set Redis cache:refresh_token:{token} TTL 30 ngày |
| 4 | Core Service | Ghi login_audit_logs (LOGIN_SUCCESS/FAILED) | – | Cùng transaction DB |
| 5 | Core Service → Client | Set-Cookie access_token (15p) + Set-Cookie refresh_token (30d), cả 2 HttpOnly; Secure; SameSite=Strict | REST response | Chi tiết cookie: `05-cookie-auth-flow.md` E.1 |

### **F.2 Đăng nhập qua bên thứ 3 (Google/Facebook/Apple) — "Kiểu B", frontend hứng code**

> Chọn kiểu frontend tự hứng `code` (thay vì để provider redirect thẳng vào backend) vì dùng
> chung được đúng 1 API cho cả web và mobile app sau này — mobile native SDK cũng chỉ đưa `code`/
> `id_token` cho app rồi app tự POST lên, không có khái niệm "provider redirect thẳng vào
> backend" trên mobile. Xem thêm `services/03-core-service.md` mục "Redirect flow — Kiểu B".

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client (frontend) → Provider (Google/FB/Apple) | Redirect tới trang consent của provider, `redirect_uri` trỏ về 1 route của FRONTEND (không phải backend) | REST (browser redirect) | Không qua API Gateway ở bước này |
| 2 | Provider → Client (frontend) | Redirect trình duyệt về lại route frontend đã khai, kèm `?code=...` | REST (browser redirect) | Backend không thấy request này |
| 3 | Client (frontend) → API Gateway → Core Service | Đọc `code` từ URL bằng JS, POST lên backend | REST | `POST /auth/oauth/{provider}/callback {code}` |
| 4 | Core Service → Provider | Đổi code lấy id_token/access_token + profile (`GoogleOAuthStrategy.exchangeCode`) | REST (ra ngoài internet) | Không phải gRPC vì đây là gọi API bên thứ 3, không phải nội bộ |
| 5 | Core Service | Tìm `user_oauth_providers` theo `(provider, provider_user_id)` — có thì login, chưa có thì tạo user mới (1 provider/user, KHÔNG link nếu email đã tồn tại — xem `03-core-service.md`) | – | `OAuthServiceImpl.loginWithCallback` |
| 6 | Core Service | Nếu account có 2FA bật → phát `pre_auth_token`, bắt verify 2FA xong mới qua bước 7 (`AuthService#completeLogin`, dùng chung với login password) | – | Giống hệt luồng 2FA ở F.1 |
| 7 | Core Service → Client | 200 JSON + Set-Cookie access_token + refresh_token (không redirect — đây là response của 1 API JSON bình thường) | REST |   |

### **F.3 Đổi mật khẩu**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway → Core Service | POST /auth/change-password {old, new} | REST |   |
| 2 | Core Service | Đọc `X-User-Id` (đã verify JWT ở Gateway), verify old password | – | Không cần VerifySession — access_token của chính request này đã xác thực rồi |
| 3 | Core Service | Hash mật khẩu mới, update users.password_hash | – |   |
| 4 | Core Service | Revoke TẤT CẢ refresh_token hiện có (xoá `cache:refresh_token:*` + update `revoked_at` trong user_sessions) + set `cache:jwt_revoked_before:{user_id}=now()` | – | Bảo mật: đổi mật khẩu phải logout toàn bộ thiết bị khác NGAY (kể cả access_token đang lưu hành), xem `05-cookie-auth-flow.md` E.6 |
| 5 | Core Service | Publish user.password_changed (tuỳ chọn, để Notification gửi email cảnh báo bảo mật) | RabbitMQ |   |
| 6 | Core Service → Client | 200 OK, Set-Cookie access_token + refresh_token mới cho phiên hiện tại | REST | Chỉ phiên vừa đổi mật khẩu được cấp lại token mới, các thiết bị khác bị đăng xuất ngay |

### **F.4 Đăng xuất (1 thiết bị / tất cả thiết bị)**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway → Core Service | POST /auth/logout (1 thiết bị) hoặc /auth/logout-all | REST |   |
| 2 | Core Service | Xoá `cache:refresh_token:{token}` (1 hoặc nhiều key) + blacklist `jti` access_token hiện tại (`cache:jwt_blacklist:{jti}`); nếu logout-all thì set thêm `cache:jwt_revoked_before:{user_id}=now()` | – | logout-all: SCAN refresh_token theo user_id + DEL hàng loạt, xem `05-cookie-auth-flow.md` E.6 |
| 3 | Core Service | Update user_sessions.revoked_at, revoke_reason=logout/all_logout | – |   |
| 4 | Core Service → Client | Set-Cookie: access_token=; Max-Age=0 và refresh_token=; Max-Age=0 | REST | Xoá cả 2 cookie phía client |
| 5 | (nếu logout-all) Core Service | Publish user.session_revoked | RabbitMQ | Realtime Gateway consume → force-disconnect toàn bộ WS connection của user đó |

### **F.5 Xử lý khi access_token hết hạn / bị thu hồi**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client gửi request bất kỳ | Kèm Cookie access_token (JWT) | REST/WS |   |
| 2 | API Gateway / Realtime Gateway | Verify chữ ký JWT tại chỗ (public key) + check `exp` + Redis GET `cache:jwt_revoked_before:{sub}` / `cache:jwt_blacklist:{jti}` | – (không gRPC) | Xem `05-cookie-auth-flow.md` E.4/E.6 |
| 3a | Trường hợp JWT hết hạn (`exp` qua rồi) | – | – | error_code = TOKEN_EXPIRED |
| 3b | Trường hợp JWT bị revoke (match `jwt_revoked_before` hoặc `jwt_blacklist`) | – | – | error_code = TOKEN_REVOKED |
| 4a | API Gateway → Client | 401 { error_code } | REST | TOKEN_EXPIRED: client tự gọi `/auth/refresh` rồi retry. TOKEN_REVOKED: xoá state đăng nhập, redirect login, không thử refresh |
| 4b | Realtime Gateway → Client | Đóng WS connection với close code 4401 (tự định nghĩa) | WS | Client-side: dừng reconnect tự động, redirect login (WS không tự refresh được, xem E.8) |
| 5 | Nếu TOKEN_EXPIRED | Client → POST /auth/refresh (Cookie refresh_token) → Core Service RefreshAccessToken → access_token + refresh_token mới → client retry request gốc | REST + gRPC nội bộ | Chi tiết đầy đủ luồng refresh: `05-cookie-auth-flow.md` E.5 |

### **F.6 Gửi tin nhắn text (1-1 và nhóm)**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway | POST /messages {conversation_id, content, message_id (client-gen)} | REST |   |
| 2 | API Gateway | Verify JWT tại chỗ (không gRPC) | – | Lấy user_id từ claim `sub`, gán header X-User-Id |
| 3 | API Gateway → Messaging Service | Forward kèm X-User-Id | REST |   |
| 4 | Messaging Service → Core Service | CheckFriendship / CheckBlock (chỉ áp dụng chat 1-1) | gRPC | Chat nhóm bỏ qua bước này, thay bằng bước 4b |
| 4b | Messaging Service → Core Service (module Group) | GetGroupMembers / CheckGroupRole (chỉ áp dụng chat nhóm) | gRPC | Kiểm tra user còn trong nhóm không trước khi cho gửi |
| 5 | Messaging Service | Insert message vào MongoDB + insert outbox_events (cùng transaction/cùng document) | – |   |
| 6 | Messaging Service (Go relay worker) | Poll outbox → publish message.sent | RabbitMQ (chat.exchange) |   |
| 7 | Realtime Gateway | Consume message.sent → tra cache:ws:room:{conversation_id} → push tới các connection đang mở màn hình đúng conversation | WS |   |
| 8 | Notification Service | Consume message.sent song song → kiểm tra conversation_settings.is_muted, mute_until → push FCM/APNs cho người không đang mở app | RabbitMQ + REST ra ngoài (FCM/APNs) | Chạy song song bước 7, không chờ nhau |
| 9 | Messaging Service → Client A (người gửi) | 200 OK ngay sau bước 5, không chờ bước 6-8 | REST | Client A optimistic-update UI trước khi có response, response chỉ xác nhận "đã lưu" |

### **F.7 Gửi tin nhắn kèm file nặng (chunked upload)**

**Ràng buộc thứ tự BẮT BUỘC (chống race condition): bước 1 phải chạy xong và có response trước khi bước 2 được gọi. Chỉ từ bước 5 trở đi (upload chunk) mới thực sự chạy song song/độc lập. Lý do: media.upload_completed ở bước 10 tìm message theo context_id=message_id — nếu chưa có message tồn tại (vì chưa làm xong bước 1), event sẽ không tìm thấy đối tượng để cập nhật.**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway → Messaging Service | POST /messages {message_id (client-gen UUID), conversation_id, content, status=UPLOADING, media_url=null} | REST | PHẢI hoàn tất, có response 200 kèm message_id, TRƯỚC KHI làm bước 2. Bubble hiện ngay ở client với trạng thái "đang tải" |
| 2 | Client → API Gateway → Media Service | POST /media/upload/init {file_name, file_size, mime_type, context_type=message, context_id=message_id (lấy từ bước 1)} | REST | context_id chính là message_id vừa tạo — đây là "sợi dây" nối 2 service lại với nhau |
| 3 | Media Service → Cloudflare R2 | Khởi tạo multipart upload, sinh presigned URL cho từng chunk | REST tới R2 (S3-compatible API) | Media Service KHÔNG nhận bytes file |
| 4 | Media Service | Insert media_uploads (status=PENDING, context_type=message, context_id=message_id), lưu multipart_upload_id, total_chunks | – |   |
| 5 | Media Service → Client | Trả về upload_id + danh sách presigned URL | REST |   |
| 6 | Client → Cloudflare R2 | PUT từng chunk (5MB/chunk) trực tiếp lên R2, song song nhiều chunk | R2 (bypass toàn bộ backend) | Từ đây trở đi mới thực sự song song/độc lập — backend không phải trung gian, tránh nghẽn băng thông server |
| 7 | Client → Media Service | POST /media/upload/chunk-ack {chunk_index, etag} (mỗi chunk xong) | REST (WS cũng được để đỡ tốn round-trip) | Cập nhật chunks_uploaded để tính % tiến trình |
| 8 | Realtime Gateway | Push upload progress % về đúng màn hình chat (nếu dùng WS ở bước 7) | WS |   |
| 9 | Client → Media Service | POST /media/upload/complete | REST |   |
| 10 | Media Service → Cloudflare R2 | CompleteMultipartUpload | REST tới R2 |   |
| 11 | Media Service | Update status=PROCESSING, publish media.upload_completed {context_id=message_id, cdn_url} | RabbitMQ (media.exchange) | Nếu là video: đồng thời trigger FFmpeg worker tạo thumbnail (bước 11b) |
| 11b | Media Service (FFmpeg worker) | Extract frame giây 1, upload thumbnail lên R2, update status=COMPLETED | – | Chạy async, publish media.processing_done khi xong |
| 12 | Media Service (ClamAV worker) | Quét virus async sau khi COMPLETED | – | Nếu nhiễm → status=QUARANTINE, publish media.quarantine |
| 13 | Messaging Service | Consume media.upload_completed → tìm message theo message_id=context_id (chắc chắn tồn tại nhờ ràng buộc thứ tự ở bước 1) → update message.media_url, status=SENT | RabbitMQ | Nếu không tìm thấy message (trường hợp lỗi hy hữu) → retry với backoff, không được silently drop event |
| 14 | Realtime Gateway | Consume event cập nhật từ Messaging → push message.update{cdn_url} tới màn hình chat | WS | Ảnh/video hiện ra ngay khi vừa upload xong, không cần đợi cả cuộc hội thoại reload |

### **F.8 Thread reply**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway → Messaging Service | POST /messages {conversation_id, content, reply_to_message_id} | REST |   |
| 2 | Messaging Service | Đọc message gốc (reply_to_message_id): nếu message đó đã có thread_root_id → dùng chính root đó; nếu chưa có → chính message đó trở thành root | – | Ràng buộc chống đệ quy nằm ở đây, không phải ở client |
| 3 | Messaging Service | Insert message mới với thread_root_id đã xác định ở bước 2, atomic increment thread_root.thread_reply_count | – |   |
| 4 | Messaging Service (relay worker) | Publish message.thread_replied | RabbitMQ (chat.exchange) |   |
| 5 | Realtime Gateway | Push cập nhật thread_reply_count tới người đang xem root message (không phải toàn conversation, chỉ ai đang mở đúng thread đó) | WS | Cần client gửi screen_register riêng cho từng thread đang mở, giống cơ chế screen:chat:{id} |

### **F.9 Tin nhắn tạm thời / bí mật (ephemeral) — vòng đời đầy đủ**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → Messaging Service | Gửi message với is_ephemeral=true, ephemeral_mode=TIMED hoặc AFTER_VIEW | REST |   |
| 2a | (TIMED) Messaging Service | expires_at = sent_at + N giây, set ngay lúc insert | – |   |
| 2b | (AFTER_VIEW) Messaging Service | expires_at = null lúc insert | – | Chưa đếm ngược cho tới khi có người xem |
| 3 | Realtime Gateway | Fan-out message.sent như luồng thường (F.6 bước 6-8) | RabbitMQ + WS |   |
| 4 | (AFTER_VIEW) Client B mở màn hình chat, gửi message.seen | WS → Messaging Service | – | Giống cơ chế seen thường |
| 5 | (AFTER_VIEW) Messaging Service | Consume message.seen của message ephemeral → set expires_at = now() + N giây | RabbitMQ |   |
| 6 | MongoDB TTL monitor (built-in, không phải code tự viết) | Tự động xoá document khi qua expires_at | – | Chạy nền mỗi ~60s, không đảm bảo xoá đúng giây tuyệt đối |
| 7 | Vấn đề UX cần chốt | Khi message bị TTL xoá, các client đang mở màn hình KHÔNG tự biết message đã biến mất (Mongo không tự bắn event). Cần: Messaging Service chạy 1 worker riêng quét message sắp hết hạn (hoặc dùng MongoDB Change Stream lắng nghe delete) → publish message.expired → WS push xoá bubble khỏi UI đang mở. | RabbitMQ + WS | Đây là phần CHƯA có trong schema — cần bổ sung nếu muốn UX mượt (im lặng biến mất khi F5 là chấp nhận được, nhưng biến mất ngay trước mắt cần thêm cơ chế này) |

### **F.10 Cuộc gọi 1-1 / nhóm + thao tác trong lúc call (mute/camera/share màn hình)**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client (Caller) → API Gateway → Call Service | POST /calls {callee_id hoặc group_id, call_type} | REST |   |
| 2 | Call Service | Insert call_sessions (status=RINGING), gọi API LiveKit/Agora tạo room | REST tới LiveKit/Agora (bên thứ 3, không phải gRPC nội bộ) |   |
| 3 | Call Service | Sinh JWT room token cho Caller, trả về ngay | REST |   |
| 4 | Call Service → Notification Service | SendVoIPPush(callee_id, payload kèm token) | gRPC | Cần độ trễ thấp nên dùng gRPC thay vì RabbitMQ ở bước này |
| 5 | Notification Service | Gửi VoIP Push (PushKit iOS / FCM high-priority Android) | REST ra ngoài (APNs/FCM) |   |
| 6 | Callee bấm nghe | App dùng token có sẵn trong payload push, gọi LiveKit/Agora SDK connect room | WebRTC (do SDK lo, không qua backend) |   |
| 7 | Client (Callee) → Call Service | POST /calls/{id}/answer | REST | Update status=ACTIVE, started_at |
| 8 | Call Service (relay) | Publish call.answered | RabbitMQ (call.exchange) |   |
| 9 | Realtime Gateway | Push call.answered tới Caller | WS | UI Caller chuyển "đang đổ chuông" → "đã kết nối" |
| 10 | Trong lúc call: bấm mute/camera | Client → Call Service: POST /calls/{id}/participant-state {is_mic_on, is_camera_on} | REST | Đồng thời client tự tắt track ngay ở tầng WebRTC (không đợi response) để phản hồi tức thì |
| 11 | Call Service | Update call_participants, publish call.participant_update | RabbitMQ |   |
| 12 | Realtime Gateway | Push tới toàn bộ participant khác trong room để hiện icon "đã tắt mic" | WS |   |
| 13 | Trong lúc call: share màn hình | Client gọi getDisplayMedia (browser/OS API) → publish thêm 1 video track vào room qua LiveKit/Agora SDK | WebRTC trực tiếp | Client tự kiểm tra "đã có ai đang share chưa" bằng cách hỏi trạng thái room hiện tại trước khi share |
| 14 | Call Service | Nhận báo có người share (qua webhook của LiveKit/Agora hoặc client tự POST) → nếu đã có người khác đang share → reject, trả lỗi ALREADY_SHARING | REST + webhook từ LiveKit/Agora | Ràng buộc "1 người share tại 1 thời điểm" nằm ở business logic này, không phải giới hạn kỹ thuật SFU |
| 15 | Kết thúc call | Client → Call Service: POST /calls/{id}/end → update status=ENDED, duration_sec → publish call.ended | REST + RabbitMQ | Messaging Service consume call.ended → tạo message loại MESSAGE_CALL_LOG trong conversation |

### **F.11 Tạo sự kiện / lịch hẹn nhóm + nhắc nhở**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client (Admin/Owner) → API Gateway → Core Service | POST /groups/{id}/events {title, event_time, event_timezone, reminder_minutes_before} | REST |   |
| 2 | Core Service | CheckGroupRole(user_id, group_id) phải là ADMIN/OWNER | gRPC nội bộ (Core tự xử lý vì Group module nằm cùng Core) |   |
| 3 | Core Service | Insert group_events, publish group.event_created | RabbitMQ |   |
| 4 | Notification Service | Consume group.event_created → push thông báo tạo event mới tới toàn bộ member | RabbitMQ + push | – |
| 5 | Core Service (scheduler/cron worker riêng) | Quét định kỳ group_events có event_time - reminder_minutes_before <= now() AND reminded_at IS NULL | – | reminded_at là field CẦN BỔ SUNG vào schema (đã nêu ở lần review trước) để chống nhắc trùng |
| 6 | Core Service | Publish group.event_reminder, ngay sau đó set reminded_at = now() | RabbitMQ | Set reminded_at PHẢI làm ngay sau publish, cùng transaction nếu được, để tránh worker chạy 2 lần cùng lúc publish trùng |
| 7 | Notification Service | Consume group.event_reminder → push nhắc nhở tới toàn bộ member đã RSVP GOING (hoặc tất cả nếu chưa RSVP) | RabbitMQ + push |   |

### **F.12 Cập nhật avatar / display_name / nickname → đồng bộ hiển thị tin nhắn cũ**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client → API Gateway → Core Service | PATCH /profile {avatar_url, display_name} | REST |   |
| 2 | Core Service | Update users, xoá cache:user:{id} trong Redis (invalidate ngay, không đợi TTL 5 phút tự hết) | – | Đây là bước hay bị quên — nếu chỉ update DB mà không xoá cache, các service khác vẫn đọc thông tin cũ tới 5 phút |
| 3 | Core Service (relay) | Publish user.profile_updated | RabbitMQ (user.exchange) |   |
| 4 | Realtime Gateway | Consume → push profile.updated{user_id, display_name, avatar_url} tới TẤT CẢ WS connection đang mở của những người có chung conversation/group với user này | WS | Không push tới tất cả user toàn hệ thống — chỉ tới ai đang "nhìn thấy" user đó trên UI |
| 5 | Client (người khác) nhận WS event | Client tự cập nhật tên/avatar hiển thị trên các tin nhắn CŨ đã render sẵn trong UI (không phải sửa dữ liệu tin nhắn) | – | Nguyên tắc quan trọng: messages KHÔNG lưu snapshot tên/avatar người gửi tại thời điểm gửi — luôn hiển thị theo thông tin user MỚI NHẤT, join tại thời điểm render ở client, không phải lưu cứng trong message document |
| 6 | Ngoại lệ: forwarded_from_sender, reply_snapshot | 2 field này trong message CÓ lưu snapshot tên tại thời điểm forward/reply — đây là chủ đích (giữ ngữ cảnh lịch sử "đã reply ai lúc đó"), không áp dụng nguyên tắc ở bước 5 | – | Cần nói rõ trong code review để tránh nhầm 2 loại field này với nhau |
| 7 | Social Service | Cũng consume user.profile_updated để cập nhật hiển thị tên/avatar trên story/post cũ theo cùng nguyên tắc bước 5 | RabbitMQ |   |

### **F.13 Cập nhật last_seen của các user trong group**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Actor / Service** | **Hành động** | **Giao thức** | **Ghi chú** |
| 1 | Client User A đóng app / mất kết nối WS | Realtime Gateway phát hiện connection đóng (chủ động hoặc do TTL heartbeat 35s hết hạn) | – |   |
| 2 | Realtime Gateway | Kiểm tra cache:ws:user:{A} (Set các connID) — chỉ coi là offline nếu Set này RỖNG (không còn thiết bị nào khác đang mở) | – | Quan trọng: User A mở cả điện thoại lẫn web thì đóng 1 thiết bị KHÔNG được coi là offline |
| 3 | Realtime Gateway | Publish presence.offline{user_id, last_seen_at=now()} | RabbitMQ (presence.exchange) |   |
| 4 | Core Service | Consume presence.offline → update users.last_seen_at = now() | RabbitMQ | Ghi DB bất đồng bộ qua event, không ghi đồng bộ mỗi lần connect/disconnect để tránh spam write |
| 5 | Client B đang xem danh sách thành viên nhóm chứa User A | Không tự động nhận push real-time cho last_seen (khác với presence online/offline vốn có push) — last_seen chỉ hiển thị đúng khi Client B GỌI LẠI API lấy thông tin nhóm/profile | REST (pull, không phải push) | Quyết định thiết kế: last_seen là thông tin "tra cứu khi cần xem", không cần độ chính xác realtime tới từng giây như trạng thái online — nếu muốn push realtime, cần thêm bước Realtime Gateway fan-out presence.offline tới members cùng group (tốn thêm băng thông WS cho lợi ích nhỏ) |
| 6 | Quyền xem | Trước khi trả last_seen, phải áp dụng who_can_see_online_status (mục 3.4 user_privacy_settings) — không phải ai trong nhóm cũng được thấy last_seen của người khác nếu người đó đã tắt trong Privacy Settings | – | Áp dụng cả khi trả last_seen lẫn khi trả trạng thái online/offline |
