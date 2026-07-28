# **SERVICE 4 – MESSAGING SERVICE**

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Java 21 / Spring Boot 3 |
| **Database** | MongoDB (messages, conversations) + PostgreSQL (conversation metadata, pins) |
| **Cache** | Redis (conversation list, pin cache, seen status buffer) |
| **Outbox** | outbox_events collection trong MongoDB |
| **gRPC exposed** | CreateConversation, GetConversation, GetMessages, GetPinnedMessages |
| **gRPC calls** | IdentityService (CheckFriendship, CheckBlock), GroupService (GetMembers) |
| **RabbitMQ consume thêm** | `group.member_joined`, `group.member_removed`, `group.deleted` (từ `group.exchange`, Core Service publish) — đồng bộ `conversations.participant_ids`, xem mục 4.11 |

## **4.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Gửi tin nhắn text** | Validate nội dung, tạo Message document, publish message.sent event. WS GW fan-out đến conversation members. |
| **2** | **Gửi ảnh / video / file** | Nhận media_upload_id (đã upload qua Media Service), tạo message với cdn_url. Streaming hiển thị ngay, không đợi upload xong. |
| **3** | **Gửi GIF** | Tích hợp Giphy/Tenor URL, tạo message_type=MESSAGE_GIF. |
| **4** | **Gửi Link Preview** | Parse URL metadata (OGP), tạo message với link_preview object. |
| **5** | **Gửi Sticker** | message_type=MESSAGE_STICKER, sticker_id reference. |
| **6** | **Trạng thái: Sent** | Message tạo thành công → status=SENT, 1 tick xám. |
| **7** | **Trạng thái: Delivered** | WS GW xác nhận deliver đến app đang mở → publish message.delivered → update status=DELIVERED. |
| **8** | **Trạng thái: Seen (1-1)** | Người nhận mở màn hình chat → publish message.seen → update seen_at, status=SEEN → 2 tick xanh. |
| **9** | **Seen group** | Insert message_seen_logs per member. WS push seen_by list update về room. |
| **10** | **Thu hồi tin nhắn (chính mình)** | Set deleted_for_sender=true. Chỉ ẩn phía người gửi. |
| **11** | **Thu hồi tin nhắn 2 phía** | Set deleted_for_all=true trong giới hạn thời gian (mặc định 10 phút). WS push message.deleted đến conversation. |
| **12** | **Chuyển tiếp tin nhắn** | Tạo message mới với forwarded_from_id. Hiện "Được chuyển tiếp". |
| **13** | **Trả lời tin nhắn (Reply)** | Lưu reply_to_id + reply_snapshot. Hiển thị preview tin nhắn gốc. |
| **14** | **React emoji** | Upsert reactions array trong message. Publish message.reacted event. |
| **15** | **Ghim tin nhắn** | Set is_pinned=true. Lưu conversation_pins. Notify members. |
| **16** | **Bỏ ghim** | Set is_pinned=false, xoá pin record. |
| **17** | **Xem danh sách tin ghim** | Query conversation_pins sorted by pinned_at. |
| **18** | **Tìm kiếm trong chat** | Full-text search MongoDB text index trên content, lọc theo date range. Highlight kết quả. |
| **19** | **Phân loại media trong chat** | Tab ảnh/video, tab file, tab link – query theo message_type. |
| **20** | **Đổi background cuộc trò chuyện** | Update conversation_settings.custom_wallpaper_url per user. |
| **21** | **Tắt thông báo cuộc trò chuyện** | Update conversation_settings.is_muted, mute_until (cùng transaction/document) → insert outbox_events → publish `conversation.mute_updated` để Notification Service tự cập nhật bản sao của nó (xem mục 4.10 — Notification KHÔNG được đọc thẳng MongoDB này). |
| **22** | **Danh sách cuộc trò chuyện** | Trả conversations sorted by last_activity_at, kèm unread_count per user. |
| **23** | **Lịch sử tin nhắn (pagination)** | Cursor-based pagination (before_id). Load 30 message/trang. |
| **24** | **Pending message (người lạ)** | Nếu chưa kết bạn và who_can_message=EVERYONE → tạo pending_messages thay vì conversation thông thường. Không gửi seen khi người nhận xem. |
| **25** | **Chấp nhận / Từ chối pending** | Accept → convert thành DM conversation. Reject → xoá. Publish tương ứng. |
| **26** | **@mention cá nhân** | Parse @username trong content → ghi mentions array → trigger notification.mention. |
| **27** | **Mark all read** | Update last_read_at trong conversation_members, reset unread_count. |
| **28** | **Xoá toàn bộ lịch sử (chỉ mình)** | Soft delete tất cả messages phía người dùng đó trong conversation. |
| **29** | **Sửa tin nhắn (edit)** | Chỉ sender, chỉ `message_type=MESSAGE_TEXT`, trong vòng 15 phút kể từ `sent_at`. Không sửa được nếu đã `deleted_for_all`/`deleted_for_sender`. Lưu bản cũ vào `edit_history` trước khi ghi đè `content`, set `edited_at=now()`. Publish `message.edited` → WS GW cập nhật bubble + hiện nhãn "đã chỉnh sửa". Nếu nội dung mới phát sinh @mention MỚI (chưa có ở bản cũ) → publish thêm `message.mention` riêng cho user mới được tag; KHÔNG publish lại cho user đã từng được mention ở bản cũ (tránh spam). |
| **30** | **Gửi tin nhắn thoại (voice message)** | Client ghi âm → upload qua Media Service như file thường (mục 2), nhận `media_upload_id` + `cdn_url`. Tạo message với `message_type=MESSAGE_VOICE`, `media_mime_type` (audio/webm, audio/aac...), `media_duration_sec` bắt buộc, `waveform_data` (mảng amplitude do client tính sẵn, gửi kèm lúc tạo message) để vẽ sóng âm trên UI. Không lưu file audio trong DB — chỉ lưu `media_url` trỏ CDN, giống hệt ảnh/video (xem `skills/database-per-service.md` — chỉ Media Service được gọi R2 API trực tiếp). |

## **4.2 Naming Convention – Java (same as Identity)**

public class MessageDocument { }   // MongoDB document

public class ConversationDocument { }

public enum MessageType { MESSAGE_TEXT, MESSAGE_IMAGE, MESSAGE_VIDEO, MESSAGE_FILE, MESSAGE_GIF, MESSAGE_LINK, MESSAGE_STICKER, MESSAGE_STORY_REPLY, MESSAGE_CALL_LOG, MESSAGE_VOICE }

public enum MessageStatus { SENT, DELIVERED, SEEN }

public enum PendingMessageStatus { WAITING, ACCEPTED, REJECTED, EXPIRED }

## **4.3 Enums**

**Enum: MessageType**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **MESSAGE_TEXT** | Tin nhắn văn bản |
| **MESSAGE_IMAGE** | Ảnh (JPEG/PNG/WebP/GIF) |
| **MESSAGE_VIDEO** | Video (MP4/MOV) |
| **MESSAGE_FILE** | File đính kèm (docx/xlsx/pdf/zip...) |
| **MESSAGE_GIF** | GIF từ Giphy/Tenor |
| **MESSAGE_LINK** | Đường dẫn có preview |
| **MESSAGE_STICKER** | Sticker |
| **MESSAGE_STORY_REPLY** | Reply vào story |
| **MESSAGE_CALL_LOG** | Log cuộc gọi (missed/ended) |
| **MESSAGE_VOICE** | Tin nhắn thoại (ghi âm gửi trực tiếp trong chat) |

**Enum: MessageStatus**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **SENT** | Server đã lưu – 1 tick xám |
| **DELIVERED** | App người nhận nhận được – 2 tick xám |
| **SEEN** | Người nhận đã xem – 2 tick xanh |

## **4.4 Database Schema**

### **ð messages (MongoDB collection)  [MongoDB]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK MongoDB |
| **message_id** | UUID | NO | client gen | Idempotency key từ client – UNIQUE index |
| **conversation_id** | ObjectId | NO | – | Cuộc trò chuyện |
| **sender_id** | UUID | NO | – | Người gửi |
| message_type | String | NO | – | Enum MessageType |
| content | String | YES | NULL | Nội dung text/caption |
| media_url | String | YES | NULL | Cloudflare CDN URL |
| media_thumbnail_url | String | YES | NULL | Thumbnail video CDN |
| media_size_bytes | Long | YES | NULL | Kích thước file |
| media_mime_type | String | YES | NULL | video/mp4, image/jpeg... |
| media_duration_sec | Int | YES | NULL | Thời lượng video/audio |
| media_width | Int | YES | NULL | Chiều rộng ảnh/video (px) |
| media_height | Int | YES | NULL | Chiều cao ảnh/video (px) |
| waveform_data | Array\<Int\> | YES | NULL | Mảng amplitude (0-100) client tính sẵn lúc ghi âm, chỉ dùng khi `message_type=MESSAGE_VOICE`, để vẽ sóng âm trên UI mà không cần decode lại file audio |
| link_preview | Object | YES | NULL | { url, title, description, thumbnail_url, site_name } |
| **sticker_id** | String | YES | NULL | ID sticker |
| **reply_to_id** | ObjectId | YES | NULL | ID message được reply |
| reply_snapshot | Object | YES | NULL | { sender_name, content_preview, message_type } |
| **forwarded_from_id** | ObjectId | YES | NULL | Message gốc forward |
| forwarded_from_sender | String | YES | NULL | Tên người gửi gốc |
| is_pinned | Boolean | NO | false | Đang ghim |
| pinned_at | Date (UTC) | YES | NULL | Thời điểm ghim |
| pinned_by | UUID | YES | NULL | Ai ghim |
| deleted_for_sender | Boolean | NO | false | Xoá phía người gửi |
| deleted_for_all | Boolean | NO | false | Thu hồi 2 phía |
| deleted_at | Date (UTC) | YES | NULL | Thời điểm thu hồi |
| reactions | Array<Reaction> | NO | [] | [{user_id, emoji, reacted_at}] |
| mentions | Array<UUID> | NO | [] | User được @mention |
| message_status | String | NO | SENT | Enum MessageStatus (chỉ DM) |
| delivered_at | Date (UTC) | YES | NULL | UTC – delivered |
| seen_at | Date (UTC) | YES | NULL | UTC – seen (DM only) |
| sent_at | Date (UTC) | NO | now() | UTC – gửi |
| edited_at | Date (UTC) | YES | NULL | UTC – lần sửa gần nhất, null = chưa từng sửa |
| edit_history | Array<Object> | NO | [] | [{content, edited_at}] — bản nội dung TRƯỚC mỗi lần sửa, dùng cho mục đích kiểm duyệt/điều tra report (mục 3.16 Core Service), KHÔNG hiển thị cho user thường |

### **ð conversations (MongoDB collection)  [MongoDB]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| type | String | NO | – | DIRECT / GROUP / SELF — xem mục 4.12 cho SELF |
| participant_ids | Array<UUID> | NO | – | Members (DIRECT: 2 người, SELF: đúng 1 người — chính chủ) |
| **direct_pair_key** | String | YES | NULL | Set khi type=DIRECT (2 user_id ghép theo thứ tự alphabet, VD `{uuid_nhỏ_hơn}_{uuid_lớn_hơn}`) HOẶC type=SELF (chính `user_id` đó, không ghép cặp) — UNIQUE index (sparse, chỉ áp dụng field có giá trị) chống tạo trùng conversation cho cùng 1 cặp/cùng 1 user, xem mục 4.11 |
| is_pending_request | Boolean | NO | false | true = DM tạo do tin nhắn "người lạ" (pending), CHƯA hiện trong danh sách chat của receiver cho tới khi accept — xem mục 4.11 |
| **last_message_id** | ObjectId | YES | NULL | Tin nhắn cuối |
| last_message_preview | String | YES | NULL | Preview nội dung |
| **last_message_sender_id** | UUID | YES | NULL | Người gửi cuối |
| last_activity_at | Date (UTC) | NO | now() | UTC – hoạt động cuối |
| created_at | Date (UTC) | NO | now() | UTC |

### **ð message_seen_logs (MongoDB)  [MongoDB – Group seen]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **message_id** | ObjectId | NO | – | Tin nhắn |
| **conversation_id** | ObjectId | NO | – | Nhóm |
| **user_id** | UUID | NO | – | Người đã xem |
| seen_at | Date (UTC) | NO | now() | UTC |

### **ð pending_messages (MongoDB)  [MongoDB]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **message_id** | ObjectId | NO | – | Ref → messages |
| **conversation_id** | ObjectId | NO | – | Ref → conversations (đã tạo ngay từ đầu với `is_pending_request=true`, xem mục 4.11) |
| **sender_id** | UUID | NO | – | Người gửi (chưa kết bạn) |
| **receiver_id** | UUID | NO | – | Người nhận |
| status | String | NO | WAITING | Enum PendingMessageStatus |
| opened_at | Date (UTC) | YES | NULL | Mở xem – KHÔNG gửi seen cho sender |
| resolved_at | Date (UTC) | YES | NULL | Xử lý xong |
| created_at | Date (UTC) | NO | now() | UTC |

### **ð conversation_settings (MongoDB)  [MongoDB – Per user per conversation]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **conversation_id** | ObjectId | NO | – | Compound index |
| **user_id** | UUID | NO | – | Compound index |
| is_muted | Boolean | NO | false | Tắt thông báo |
| mute_until | Date (UTC) | YES | NULL | Hết mute (null = vĩnh viễn) |
| custom_wallpaper_url | String | YES | NULL | Background riêng CDN |
| last_read_at | Date (UTC) | YES | NULL | UTC – đọc đến đây |
| updated_at | Date (UTC) | NO | now() | UTC |

### **ð conversation_pins (MongoDB)  [MongoDB]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **conversation_id** | ObjectId | NO | – | Index |
| **message_id** | ObjectId | NO | – | Tin nhắn được ghim |
| pinned_by | UUID | NO | – | Ai ghim |
| pinned_at | Date (UTC) | NO | now() | UTC |

## **4.5 RabbitMQ Events – Messaging Service publish**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| message.sent | chat.exchange | message.sent | Gửi message thành công | WS GW, Notification, Presence |
| message.delivered | chat.exchange | message.delivered | App nhận confirm | WS GW → update tick |
| message.seen | chat.exchange | message.seen | Mở màn hình chat | WS GW → tick xanh |
| message.deleted | chat.exchange | message.deleted | Thu hồi 2 phía | WS GW → ẩn message |
| message.edited | chat.exchange | message.edited | Sửa tin nhắn text trong 15 phút | WS GW → cập nhật content + nhãn "đã chỉnh sửa" |
| message.reacted | chat.exchange | message.reacted | React emoji | WS GW → update reactions |
| message.pinned | chat.exchange | message.pinned | Ghim tin nhắn | WS GW → notify room |
| message.mention | chat.exchange | message.mention | @mention trong message | Notification Service |
| pending.received | chat.exchange | pending.received | Người lạ gửi tin nhắn | Notification Service |
| pending.accepted | chat.exchange | pending.accepted | Accept pending | Identity Service |
| conversation.mute_updated | chat.exchange | conversation.mute_updated | User đổi is_muted/mute_until 1 cuộc trò chuyện | Notification Service (tự cập nhật bản sao mute state, xem mục 4.10) |

## **4.6 Cấu trúc thư mục**

messaging-service/

├── src/main/java/com/chatapp/messaging/

│   ├── controller/

│   │   ├── MessageController.java

│   │   ├── ConversationController.java

│   │   └── PendingController.java

│   ├── service/

│   │   ├── MessageService.java

│   │   ├── ConversationService.java

│   │   ├── SeenService.java

│   │   ├── PinService.java

│   │   ├── PendingMessageService.java

│   │   └── SearchService.java

│   ├── domain/

│   │   ├── document/    # MessageDocument, ConversationDocument

│   │   ├── repository/  # MessageRepository, ConversationRepository

│   │   └── enums/       # MessageType, MessageStatus

│   ├── dto/

│   │   ├── request/

│   │   └── response/    # PublicMessageDTO, PublicConversationDTO

│   ├── grpc/            # IdentityGrpcClient, GroupGrpcClient

│   ├── event/           # RabbitMQ publishers, consumers

│   └── outbox/          # OutboxDocument, OutboxRelayScheduler

└── pom.xml

## **4.7 Thread reply (1 cấp, không đệ quy)**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| thread_root_id | ObjectId | YES | NULL | Nếu null → có thể là root của thread. Reply mới luôn gán vào root đó (ràng buộc ở tầng service, không dựa UI) |
| thread_reply_count | Int | NO | 0 | Denormalized, atomic increment mỗi khi có reply mới vào thread |

Field mới trong messages (mục 4.4). Sự kiện mới: message.thread_replied (routing key chat.exchange) → WS GW push cập nhật thread_reply_count tới người đang xem root message.

## **4.8 Poll / Vote**

Thêm MESSAGE_POLL vào Enum: MessageType (mục 4.3). Field bổ sung trong messages khi message_type=MESSAGE_POLL:

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| poll_question | String | NO | – | Câu hỏi |
| poll_options | Array<String> | NO | [] | Danh sách lựa chọn |
| poll_allow_multiple | Boolean | NO | false | Cho phép chọn nhiều lựa chọn |
| poll_closed_at | Date (UTC) | YES | NULL | Người tạo có thể đóng poll sớm |

Collection mới: poll_votes [MongoDB]

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| _id | ObjectId | NO | auto | PK |
| message_id | ObjectId | NO | – | Ref → messages (message loại POLL) |
| option_index | Int | NO | – | Lựa chọn được vote |
| user_id | UUID | NO | – | Người vote — UNIQUE(message_id, user_id) nếu poll chỉ cho vote 1 lựa chọn |
| voted_at | Date (UTC) | NO | now() | – |

Sự kiện mới: message.poll_voted → WS GW fan-out kết quả realtime tới conversation, không cần reload.

## **4.9 Tin nhắn tạm thời / bí mật (ephemeral)**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| is_ephemeral | Boolean | NO | false | Đánh dấu tin tạm thời |
| ephemeral_mode | String | YES | NULL | TIMED (hẹn giờ cố định) / AFTER_VIEW (hết hạn sau khi xem) |
| expires_at | Date (UTC) | YES | NULL | MongoDB TTL index trên field này — Mongo tự xoá document, không cần cron riêng |

Field mới trong messages (mục 4.4). Luồng AFTER_VIEW: lúc tạo expires_at=null → khi nhận event message.seen cho message này → set expires_at = now() + N giây → TTL index tự lo phần còn lại. Lưu ý: TTL đảm bảo không truy cập lại được sau khi xoá, không đảm bảo bí mật ở tầng server — nếu cần mức đó phải làm E2E encryption, ngoài phạm vi hiện tại.

## **4.10 Đồng bộ trạng thái mute sang Notification Service**

**Vấn đề**: `conversation_settings.is_muted`/`mute_until` (mục 4.4) nằm trong MongoDB của Messaging
Service, nhưng Notification Service (PostgreSQL riêng) cần biết trạng thái này TRƯỚC MỖI LẦN
push để quyết định có gửi hay không (mục 1, 2, 15, 16 ở `05-notification-service.md`). Theo
`database-per-service`, Notification không được đọc thẳng MongoDB này — và gọi gRPC đồng bộ mỗi
lần push cũng không hợp lý (mute là side-effect chấp nhận độ trễ vài giây, không cần realtime
tuyệt đối).

**Giải pháp — denormalize qua event (giống cách `last_message_preview` đã làm):**

1. Mọi lần user đổi `is_muted`/`mute_until` (mục #21) → Messaging Service publish
   `conversation.mute_updated` `{ user_id, conversation_id, is_muted, mute_until }` qua outbox
   như bình thường.
2. Notification Service consume event này, upsert vào bảng riêng của chính nó:
   `conversation_mute_state` (PostgreSQL, xem `05-notification-service.md` mục 5.4).
3. Khi consume `message.sent`/`message.mention`..., Notification Service tự tra bảng
   `conversation_mute_state` của chính nó (không gọi ngược Messaging) để quyết định push hay
   không — đúng nguyên tắc "denormalize dữ liệu cần thường xuyên, không gọi gRPC mỗi lần".

**Không được làm**: Messaging Service tự lọc "không publish `message.sent` nếu bị mute" — vì
`message.sent` còn dùng chung cho Realtime Gateway (hiển thị tin nhắn trên màn hình chat, không
liên quan gì đến mute) và mute là cài đặt RIÊNG TỪNG NGƯỜI trong nhóm — quyết định push cho ai
phải nằm ở phía Notification Service, nơi đang lặp qua từng người nhận để gửi push.

## **4.11 Tạo/đồng bộ conversation — 3 vấn đề đã phát hiện lúc review**

**a) Tạo conversation cho nhóm (`CreateConversation` gRPC):** khi Core Service tạo nhóm mới, nó
gọi gRPC `MessagingService.CreateConversation(type=GROUP, participant_ids=[creator])` (đồng bộ
— xem `system/03-grpc-service-catalog.md`). Messaging Service insert `conversations` với
`participant_ids` ban đầu chỉ có creator, trả về `conversation_id`. Các thành viên sau này được
thêm dần qua bước (b).

**b) Đồng bộ `participant_ids` khi nhóm đổi thành viên:** Messaging Service PHẢI consume 3 event
từ `group.exchange` (Core Service publish, xem `03-core-service.md` mục 3.10):
- `group.member_joined` → thêm `user_id` vào `conversations.participant_ids` của đúng
  `conversation_ref` tương ứng.
- `group.member_removed` → xoá `user_id` khỏi `participant_ids`. **Không xoá lịch sử tin nhắn
  cũ** — user vẫn xem được tin nhắn từ TRƯỚC lúc bị kick nếu client cache lại, nhưng không nhận
  tin nhắn MỚI nữa (vì không còn trong `participant_ids` → không được tính vào danh sách chat/
  fan-out WS).
- `group.deleted` → set `conversations.is_deleted=true` tương ứng (soft delete, khớp nguyên tắc
  chung).
Không consume 3 event này = `participant_ids` lệch dần khỏi `group_members` thật bên Core
Service — người bị kick vẫn thấy nhóm trong danh sách chat, người mới vào không thấy nhóm xuất
hiện.

**c) Chống tạo trùng conversation DIRECT (race condition):** dùng field `direct_pair_key` (mục
4.4) — trước khi insert conversation DIRECT mới, tính `direct_pair_key` = 2 user_id ghép theo
thứ tự alphabet, rồi dùng `findOneAndUpdate` với `upsert: true` trên chính field này (MongoDB
atomic operation) thay vì "check tồn tại rồi mới insert" (2 bước tách rời sẽ vẫn bị race
condition dù có check trước). UNIQUE index sparse trên `direct_pair_key` đảm bảo dù 2 request
insert cùng lúc, chỉ 1 request thắng, request còn lại nhận lỗi trùng key và tự chuyển sang dùng
conversation đã được tạo bởi request kia.

**d) `conversation_id` của tin nhắn "người lạ" (pending) — chốt hướng xử lý:** conversation
DIRECT được tạo NGAY LÚC gửi tin nhắn đầu tiên (không đợi accept), với `is_pending_request=true`
— message vẫn có `conversation_id` hợp lệ ngay từ đầu (không cần nullable). Khác biệt chỉ nằm ở
việc HIỂN THỊ:
- Phía **sender**: conversation này hiện bình thường trong danh sách chat (mục #22), có thể có
  nhãn "đang chờ phản hồi".
- Phía **receiver**: conversation này KHÔNG hiện trong danh sách chat chính — chỉ hiện trong
  mục riêng "lời nhắn chờ" (pending messages, mục #24), lọc theo
  `pending_messages.status=WAITING` của họ.
- Khi receiver **Accept** (mục #25): set `conversations.is_pending_request=false` → từ giờ hiện
  trong danh sách chat chính của cả 2 bên như bình thường, publish `pending.accepted`.
- Khi receiver **Reject**: set `pending_messages.status=REJECTED`, conversation vẫn giữ
  `is_pending_request=true` vĩnh viễn (không hiện lại trong danh sách chat của receiver trừ khi
  sender gửi tin pending mới lần nữa — tạo `pending_messages` record mới, dùng lại conversation
  cũ vì `direct_pair_key` đã tồn tại).

## **4.12 Conversation type SELF — "Cloud của tôi" (lưu file cá nhân)**

**Mục đích**: cho user 1 nơi lưu file riêng (ảnh, video, tài liệu) bằng cách nhắn tin nhắn với
chính mình — không phải tính năng mới tách biệt, mà là 1 conversation với `participant_ids` chỉ
có đúng 1 người (chính chủ), tái dùng 100% pipeline gửi tin nhắn/file đã có (F.6, F.7).

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **#** | **Quy tắc** | **Chi tiết** |
| **1** | **Tạo conversation SELF** | Lazy — tạo lần đầu user mở mục "Cloud của tôi", không tạo sẵn lúc đăng ký. `findOneAndUpdate` upsert theo `direct_pair_key = user_id` (dùng lại cơ chế chống trùng ở mục 4.11.c) để tránh race condition tạo 2 conversation SELF cùng lúc (VD: mở app trên 2 thiết bị cùng lúc lần đầu). |
| **2** | **Bỏ qua CheckFriendship/CheckBlock** | Gửi message vào conversation SELF KHÔNG gọi gRPC `CheckFriendship`/`CheckBlock` (khác F.6 bước 4) — vì không có "người kia" để check quan hệ. |
| **3** | **Không phát sinh push notification** | Notification Service consume `message.sent` như bình thường nhưng phải tự nhận diện `conversation.type=SELF` (denormalize kèm trong event payload) → luôn bỏ qua, không tạo push — vì `sender_id` và người nhận duy nhất là cùng 1 người, tự push cho chính mình vô nghĩa. |
| **4** | **Đồng bộ đa thiết bị vẫn hoạt động bình thường** | `message.sent` vẫn publish/fan-out WS như F.6 bước 6-7 — đây chính là cơ chế khiến file vừa thêm ở điện thoại hiện ngay trên web, không cần thiết kế thêm gì riêng. |
| **5** | **Không có trạng thái SEEN/DELIVERED có ý nghĩa** | `message_status` vẫn ghi nhận kỹ thuật nhưng UI không hiển thị tick xanh/xám cho conversation SELF (không có "người nhận" để phân biệt). |

## **4.13 Thư mục & tổ chức file trong conversation (DIRECT / GROUP / SELF)**

**Mục đích**: cho user tự tạo cây thư mục (lồng bao nhiêu cấp cũng được) để tổ chức lại file đã
gửi trong 1 conversation — áp dụng đồng nhất cho cả 3 loại `DIRECT`/`GROUP`/`SELF` vì cả 3 đều là
`conversations` (xem lịch sử đổi kiến trúc ở `03-core-service.md` mục 3.13).

**Ràng buộc MVP giữ nguyên như thiết kế gốc**: file phải được gửi như message trong chat trước
(F.6/F.7) — folder chỉ tổ chức lại file đã có, không upload thẳng vào folder.

### Bảng: conversation_folders [MongoDB]

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **conversation_id** | ObjectId | NO | – | Ref → conversations, dùng chung cho DIRECT/GROUP/SELF |
| parent_folder_id | ObjectId | YES | NULL | Null = folder gốc |
| **ancestor_ids** | Array<ObjectId> | NO | [] | Toàn bộ tổ tiên từ gốc tới cha trực tiếp — dùng `$in` query lấy nhanh mọi folder con cháu (xoá/move cả nhánh) mà không cần đệ quy nhiều lần (Mongo không có recursive CTE) |
| name | String | NO | – | Tên do user đặt, giới hạn 255 ký tự/cấp, KHÔNG giới hạn số cấp lồng |
| created_by | UUID | NO | – | Người tạo |
| created_at | Date (UTC) | NO | now() | – |
| updated_at | Date (UTC) | NO | now() | Đổi khi rename hoặc move sang cha khác |

### Bảng: conversation_files [MongoDB] — chỉ lưu tham chiếu, không lưu file thật

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **conversation_id** | ObjectId | NO | – | – |
| folder_id | ObjectId | YES | NULL | Null = chưa phân loại (nằm ở gốc). KHÔNG bắt buộc folder đích phải là "lá" — 1 folder có thể vừa chứa file vừa chứa folder con cùng lúc |
| **media_upload_id** | UUID | NO | – | Ref → media_uploads bên Media Service (không JOIN cross-DB, chỉ lưu ID, đúng nguyên tắc database-per-service) |
| message_id | UUID | NO | – | Message gốc chứa file này (bắt buộc, vì MVP yêu cầu file phải "gửi" như message trước) |
| added_by | UUID | NO | – | Người thêm vào folder (có thể khác người gửi gốc) |
| added_at | Date (UTC) | NO | now() | – |

**Quy tắc quyền hạn theo loại conversation:**
- `type=GROUP`: tạo folder → gRPC `CheckGroupRole` sang Core, chỉ Admin/Owner. Thêm file vào folder có sẵn → mọi Member.
- `type=DIRECT`: không có khái niệm role — cả 2 phía đều được tạo folder và thêm file ngang quyền nhau.
- `type=SELF`: chỉ 1 người trong conversation, mặc định toàn quyền.

**Thao tác move folder** (đổi `parent_folder_id` sang cha khác): validate cha mới không nằm
trong chính `ancestor_ids` của folder đang move (chặn tạo vòng lặp cha-con), sau đó cascade update
lại `ancestor_ids` cho toàn bộ folder con cháu (query theo `ancestor_ids` cũ chứa folder này).

**Không được làm**: không bao giờ để tên folder do user đặt hay `folder_id` lọt vào R2 object key
của file — R2 key sinh ra dựa trên `message_id`/`upload_id`, hoàn toàn bất biến và độc lập với
việc user rename/move file giữa các folder logic (xem `skills/naming-conventions.md` mục 9).

**Sự kiện mới** (routing key `chat.exchange`, theo đúng convention hiện có ở mục 4.5):
`conversation.folder_created`, `conversation.folder_renamed`, `conversation.folder_deleted`,
`conversation.file_added` → WS GW fan-out tới các thiết bị khác đang mở "thư viện file" của đúng
conversation đó (kể cả conversation SELF, để đồng bộ đa thiết bị như mục 4.12 #4).
