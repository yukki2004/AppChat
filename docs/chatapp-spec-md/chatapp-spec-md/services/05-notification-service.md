# **SERVICE 5 – NOTIFICATION SERVICE**

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | .NET 8 / ASP.NET Core |
| **Database** | PostgreSQL |
| **Cache** | Redis (notification count badge per user) |
| **Push** | FCM (Android), APNs (iOS), Web Push (VAPID) |
| **Consume** | Các event từ: chat.exchange, user.exchange, group.exchange, social.exchange, call.exchange |

## **5.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Push thông báo tin nhắn mới (DM)** | Consume message.sent → kiểm tra bản sao mute state của chính Notification Service (bảng `conversation_mute_state`, KHÔNG đọc thẳng MongoDB của Messaging — xem mục 5.8) → gửi FCM/APNs. |
| **2** | **Push thông báo tin nhắn nhóm** | Tương tự DM nhưng fan-out đến tất cả members, bỏ qua người gửi và người muted (tra theo từng user_id trong `conversation_mute_state`). |
| **3** | **Push @mention** | Consume message.mention → push riêng đến user được tag, không bị mute ảnh hưởng. |
| **4** | **Push lời mời kết bạn** | Consume friend.request_sent → push thông báo. |
| **5** | **Push kết bạn được chấp nhận** | Consume friend.accepted → push. |
| **6** | **Push cuộc gọi đến** | Consume call.initiated → VoIP Push (PushKit iOS / FCM high priority Android). KHÔNG thể mute. |
| **7** | **Push yêu cầu vào nhóm (admin)** | Consume group.join_request → push đến tất cả admin/owner. |
| **8** | **Push tương tác bài viết (like)** | Consume social.post_reacted → push. Có thể mute. |
| **9** | **Push bình luận bài viết** | Consume social.post_commented → push. |
| **10** | **Push reply comment** | Consume social.comment_replied → push. |
| **11** | **Push được tag trong bài viết** | Consume social.tag → push. |
| **12** | **Push tin nhắn chờ từ người lạ** | Consume pending.received → push riêng loại "Bạn có tin nhắn từ người chưa quen". |
| **13** | **In-app notification (WS)** | Sau khi lưu DB → publish event notification.new → WS GW push badge + popup. |
| **14** | **Tắt thông báo toàn bộ** | User toggle push_enabled=false → bỏ qua mọi push. |
| **15** | **Tắt thông báo per conversation** | Đọc `conversation_mute_state.is_muted` (bản sao riêng của Notification Service, xem mục 5.8) trước khi push. |
| **16** | **Mute có thời hạn** | `conversation_mute_state.mute_until` không null → compare với now() trước khi push. |
| **17** | **Tắt sound / rung** | Gửi FCM data-only message khi sound_enabled=false. |
| **18** | **Đọc thông báo (mark read)** | Update is_read=true, read_at. Publish notification.read event. |
| **19** | **Đọc tất cả thông báo** | Batch update is_read=true. |
| **20** | **Lấy danh sách thông báo** | Pagination, filter unread. |
| **21** | **Badge count** | Redis cache:notification:count:{user_id} increment/decrement. |
| **22** | **Quản lý device tokens** | Register/update/deactivate FCM/APNs token per device. |
| **23** | **Email notification** | Cho sự kiện quan trọng (đăng ký, đổi mật khẩu). Toggle được. |

## **5.2 Naming Convention – .NET (C#)**

// Class (PascalCase)

public class NotificationService { }

public class PushNotificationDispatcher { }

public record PublicNotificationDTO(Guid Id, string Type, ...);

// Enum

public enum NotificationType { MessageNew, MessageMention, FriendRequest, CallIncoming, ... }

// Interface

public interface IPushProvider { Task SendAsync(PushPayload payload); }

// Variable (camelCase trong method)

var notificationCount = await _redis.GetAsync(key);

// Property (PascalCase)

public bool IsRead { get; set; }

## **5.3 Enums**

**Enum: NotificationType**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **MessageNew** | Tin nhắn mới (DM) |
| **MessageNewGroup** | Tin nhắn nhóm |
| **MessageMention** | Được @mention |
| **PendingMessage** | Tin nhắn chờ từ người lạ |
| **FriendRequest** | Nhận lời mời kết bạn |
| **FriendAccepted** | Kết bạn được chấp nhận |
| **CallIncoming** | Cuộc gọi đến |
| **GroupJoinRequest** | Yêu cầu vào nhóm (admin) |
| **PostLike** | Ai đó thích bài viết |
| **PostComment** | Ai đó bình luận |
| **CommentReply** | Reply vào comment của mình |
| **TagInPost** | Được tag trong bài viết |
| **GroupMemberAdded** | Được thêm vào nhóm |

## **5.4 Database Schema**

### **ð notifications  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users | NO | – | Người nhận |
| **actor_id** | UUID FK→users | YES | NULL | Người thực hiện hành động |
| notification_type | VARCHAR(40) | NO | – | Enum NotificationType |
| **reference_id** | VARCHAR(36) | YES | NULL | ID liên quan (message_id, post_id…) |
| reference_type | VARCHAR(30) | YES | NULL | message / post / comment / call |
| **conversation_id** | VARCHAR(24) | YES | NULL | Conversation liên quan (redirect khi click) |
| title | VARCHAR(200) | NO | – | Tiêu đề push |
| body | TEXT | NO | – | Nội dung push |
| image_url | VARCHAR(500) | YES | NULL | Avatar actor (CDN) |
| is_read | BOOLEAN | NO | false | Đã đọc |
| read_at | TIMESTAMPTZ | YES | NULL | UTC |
| is_pushed | BOOLEAN | NO | false | Đã gửi push |
| pushed_at | TIMESTAMPTZ | YES | NULL | UTC |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð notification_settings  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **user_id** | UUID PK FK→users | NO | – | 1-1 với users |
| push_enabled | BOOLEAN | NO | true | Bật push notification |
| sound_enabled | BOOLEAN | NO | true | Âm thanh |
| vibration_enabled | BOOLEAN | NO | true | Rung |
| email_enabled | BOOLEAN | NO | false | Email notification |
| mute_all_until | TIMESTAMPTZ | YES | NULL | UTC – mute toàn bộ đến giờ này |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð device_tokens  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users | NO | – | Người dùng |
| token | TEXT | NO | – | FCM / APNs / WebPush token |
| platform | VARCHAR(10) | NO | – | android / ios / web |
| **device_id** | VARCHAR(100) | NO | – | UNIQUE(user_id, device_id) |
| device_name | VARCHAR(150) | YES | NULL | Tên thiết bị |
| is_active | BOOLEAN | NO | true | Token còn dùng được |
| registered_at | TIMESTAMPTZ | NO | now() | UTC |
| last_used_at | TIMESTAMPTZ | YES | NULL | UTC – lần cuối push thành công |
| deactivated_at | TIMESTAMPTZ | YES | NULL | UTC – token bị thu hồi |

### **ð conversation_mute_state  [PostgreSQL – bản sao denormalize từ Messaging Service]**

> KHÔNG phải nguồn sự thật — chỉ là bản sao rút gọn để tự quyết định push mà không cần đọc thẳng
> MongoDB của Messaging Service. Đồng bộ qua event `conversation.mute_updated`, xem mục 5.8.

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **user_id** | UUID FK→users | NO | – | PK composite |
| **conversation_id** | VARCHAR(24) | NO | – | PK composite – ObjectId dạng string |
| is_muted | BOOLEAN | NO | false | – |
| mute_until | TIMESTAMPTZ | YES | NULL | UTC – null = mute vĩnh viễn (nếu is_muted=true) |
| updated_at | TIMESTAMPTZ | NO | now() | UTC – thời điểm nhận event, dùng để phát hiện event đến trễ/không theo thứ tự |

### **ð notification_push_logs  [PostgreSQL – Log mỗi push gửi đi]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **notification_id** | UUID FK→notifications | NO | – | Thông báo |
| **device_token_id** | UUID FK→device_tokens | YES | NULL | Token đã gửi |
| platform | VARCHAR(10) | NO | – | android / ios / web |
| status | VARCHAR(20) | NO | – | SUCCESS / FAILED / TOKEN_INVALID |
| provider_response | TEXT | YES | NULL | FCM/APNs response body |
| sent_at | TIMESTAMPTZ | NO | now() | UTC |

## **5.5 RabbitMQ Events consume & publish**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| notification.new (publish) | notification.exchange | notification.new | Sau lưu notification DB | WS GW → in-app badge |
| notification.read (publish) | notification.exchange | notification.read | User đọc thông báo | WS GW → update badge count |
| conversation.mute_updated (consume) | chat.exchange | conversation.mute_updated | Messaging Service publish khi user đổi mute | Upsert `conversation_mute_state` — xem mục 5.8 |

## **5.6 Cấu trúc thư mục**

notification-service/

├── NotificationService.Api/

│   ├── Controllers/

│   │   └── NotificationController.cs

│   ├── Program.cs

│   └── appsettings.json

├── NotificationService.Application/

│   ├── Services/

│   │   ├── NotificationService.cs

│   │   ├── PushDispatcher.cs

│   │   └── BadgeService.cs

│   ├── EventHandlers/   # RabbitMQ consumers

│   └── DTOs/

├── NotificationService.Domain/

│   ├── Entities/        # NotificationEntity, DeviceTokenEntity

│   ├── Enums/           # NotificationType

│   └── Repositories/

├── NotificationService.Infrastructure/

│   ├── Push/            # FcmProvider, ApnsProvider, WebPushProvider

│   ├── Persistence/     # EF Core DbContext

│   └── Redis/

└── NotificationService.sln

## **5.7 Thông báo khi được react vào tin nhắn**

Thêm MessageReacted vào Enum: NotificationType (mục 5.3). Notification Service consume thêm event message.reacted (đã publish sẵn từ Messaging Service, mục 4.5) để báo "ai đó đã react vào tin nhắn của bạn".

## **5.8 Đồng bộ mute state từ Messaging Service (bắt buộc — chỗ hay bị bỏ sót)**

Notification Service KHÔNG được đọc thẳng `conversation_settings` (MongoDB của Messaging
Service) — vi phạm `database-per-service`. Thay vào đó:

1. Consume `conversation.mute_updated` (từ `chat.exchange`, Messaging Service publish mỗi khi
   user đổi mute — xem `04-messaging-service.md` mục 4.10) → upsert vào bảng
   `conversation_mute_state` (mục 5.4) của chính Notification Service.
2. Trước khi push `message.sent`/`message.mention` (mục #1, #2), tra bảng này theo
   `(user_id, conversation_id)` — không tìm thấy dòng nào = coi như `is_muted=false` (default,
   giống hành vi mặc định của Messaging Service).
3. **Xử lý event đến trễ/không theo thứ tự (RabbitMQ chỉ đảm bảo at-least-once, không đảm bảo
   đúng thứ tự)**: chỉ ghi đè nếu event mới có ngữ cảnh mới hơn — đơn giản nhất là ghi kèm
   timestamp gốc từ Messaging Service trong payload event và so với `updated_at` hiện có,
   bỏ qua nếu event nhận được cũ hơn dữ liệu đang lưu.
4. Bảng này KHÔNG cần đồng bộ ngay khi user mới đăng ký (chưa từng mute gì thì chưa có dòng nào
   — mục 2 ở trên đã xử lý mặc định `false` cho trường hợp này).
