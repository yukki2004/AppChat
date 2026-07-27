# **SERVICE 7 – CALL SERVICE**

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | .NET 8 / ASP.NET Core |
| **Database** | PostgreSQL |
| **Cache** | Redis (call session state, SFU room info) |
| **WebRTC** | Signaling server + tích hợp SFU (LiveKit / mediasoup) |
| **gRPC calls** | NotificationService.SendVoIPPush (cuộc gọi đến ưu tiên cao) |

## **7.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Khởi tạo cuộc gọi 1-1** | Caller gửi request → Call Service tạo call_session, room_id → gRPC push VoIP đến callee → WS push call.incoming. |
| **2** | **Chấp nhận cuộc gọi** | Callee accept → join SFU room → update status=ACTIVE, started_at. |
| **3** | **Từ chối cuộc gọi** | Callee reject → status=REJECTED → caller nhận call.rejected WS event. |
| **4** | **Cuộc gọi nhỡ** | Timeout 30s → status=MISSED → Notification push "Cuộc gọi nhỡ". |
| **5** | **Kết thúc cuộc gọi** | Bất kỳ bên nào end → Call Service update status=ENDED, duration_sec → WS push call.ended → lưu call log message vào Messaging. |
| **6** | **Gọi nhóm** | Tạo room SFU group, notify tất cả members nhóm. Members chủ động join/bỏ qua. |
| **7** | **Tắt / Bật mic** | Client publish WS call.mic_toggle → Call Service update participant state → fan-out. |
| **8** | **Tắt / Bật camera** | Tương tự mic. |
| **9** | **Chia sẻ toàn màn hình** | WebRTC getDisplayMedia {type: screen} → Call Service track share_target=SCREEN. |
| **10** | **Chia sẻ 1 cửa sổ app** | getDisplayMedia {type: window} → share_target=WINDOW. |
| **11** | **Chia sẻ 1 tab trình duyệt** | getDisplayMedia {type: browser} → share_target=TAB. |
| **12** | **Host control share màn hình** | Chỉ 1 người share tại 1 thời điểm. SFU từ chối screen track thứ 2. |
| **13** | **Admin mute participant** | Admin/Owner gửi request → Call Service gửi WS event call.force_mute đến participant. |
| **14** | **Reaction icon trong call** | Participant gửi reaction emoji → WS fan-out call.reaction đến toàn phòng, hiển thị animation 3s. |
| **15** | **Picture-in-Picture** | Client-side feature – Call Service không xử lý. Chỉ maintain call session. |
| **16** | **Chuyển camera trước/sau** | Client toggle – Call Service track is_front_camera trong participant state. |
| **17** | **Signaling (Offer/Answer/ICE)** | Relay WebRTC SDP offer, answer, ICE candidates giữa peers qua WS. |
| **18** | **TURN/STUN config** | Trả cấu hình TURN/STUN server cho client khi join call. |
| **19** | **Lịch sử cuộc gọi** | Trả call_sessions + participants, filter by conversation_id. |
| **20** | **Quality metrics log** | Ghi call_quality_logs: bitrate, packet_loss, jitter mỗi 5 giây. |

## **7.2 Enums**

**Enum: CallType**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **VOICE_DIRECT** | Gọi thoại 1-1 |
| **VIDEO_DIRECT** | Gọi video 1-1 |
| **VOICE_GROUP** | Gọi thoại nhóm |
| **VIDEO_GROUP** | Gọi video nhóm |

**Enum: CallStatus**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **RINGING** | Đang đổ chuông |
| **ACTIVE** | Đang kết nối |
| **ENDED** | Kết thúc bình thường |
| **MISSED** | Không nghe máy (timeout 30s) |
| **REJECTED** | Bị từ chối |
| **CANCELLED** | Caller huỷ trước khi nghe máy |

**Enum: ScreenShareTarget**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **SCREEN** | Toàn màn hình |
| **WINDOW** | Một cửa sổ ứng dụng |
| **TAB** | Một tab trình duyệt |

## **7.3 Database Schema**

### **ð call_sessions  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **room_id** | VARCHAR(100) UNIQUE | NO | – | SFU room ID |
| conversation_ref | VARCHAR(24) | NO | – | MongoDB conversation ObjectId |
| call_type | VARCHAR(20) | NO | – | Enum CallType |
| **initiator_id** | UUID FK→users | NO | – | Người khởi tạo |
| status | VARCHAR(15) | NO | RINGING | Enum CallStatus |
| max_participants | INT | NO | 50 | Giới hạn người tham gia |
| started_at | TIMESTAMPTZ | YES | NULL | UTC – có người nghe máy |
| ended_at | TIMESTAMPTZ | YES | NULL | UTC – kết thúc |
| duration_sec | INT | YES | NULL | Thời lượng |
| end_reason | VARCHAR(50) | YES | NULL | NORMAL / TIMEOUT / ERROR |
| sfu_room_token | TEXT | YES | NULL | JWT token SFU room |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð call_participants  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **call_session_id** | UUID FK→call_sessions | NO | – | Cuộc gọi |
| **user_id** | UUID FK→users | NO | – | Người tham gia |
| joined_at | TIMESTAMPTZ | YES | NULL | UTC – vào phòng |
| left_at | TIMESTAMPTZ | YES | NULL | UTC – rời phòng |
| is_mic_on | BOOLEAN | NO | true | Trạng thái mic |
| is_camera_on | BOOLEAN | NO | true | Trạng thái camera |
| is_screen_sharing | BOOLEAN | NO | false | Đang chia sẻ màn hình |
| share_target | VARCHAR(10) | YES | NULL | Enum ScreenShareTarget |
| is_front_camera | BOOLEAN | YES | NULL | Camera trước/sau (mobile) |
| is_force_muted | BOOLEAN | NO | false | Admin mute |
| sfu_participant_token | TEXT | YES | NULL | JWT participant token SFU |

### **ð call_quality_logs  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **call_session_id** | UUID FK→call_sessions | NO | – | Cuộc gọi |
| **user_id** | UUID FK→users | NO | – | Participant |
| bitrate_kbps | INT | YES | NULL | Bitrate (kbps) |
| packet_loss_pct | DECIMAL(5,2) | YES | NULL | Packet loss (%) |
| jitter_ms | INT | YES | NULL | Jitter (ms) |
| rtt_ms | INT | YES | NULL | Round-trip time (ms) |
| recorded_at | TIMESTAMPTZ | NO | now() | UTC – mỗi 5 giây |

### **ð call_reactions  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **call_session_id** | UUID FK→call_sessions | NO | – | Cuộc gọi |
| **user_id** | UUID FK→users | NO | – | Người gửi reaction |
| emoji | VARCHAR(10) | NO | – | Emoji icon |
| sent_at | TIMESTAMPTZ | NO | now() | UTC |

## **7.4 RabbitMQ Events**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| call.initiated | call.exchange | call.initiated | Cuộc gọi được tạo | Notification (VoIP push) |
| call.answered | call.exchange | call.answered | Callee chấp nhận | WS GW → caller update UI |
| call.ended | call.exchange | call.ended | Cuộc gọi kết thúc | Messaging (log message), WS GW |
| call.missed | call.exchange | call.missed | Timeout không nghe máy | Notification (push missed call) |
| call.rejected | call.exchange | call.rejected | Bị từ chối | WS GW → caller update UI |
| call.participant_update | call.exchange | call.participant_update | Mic/camera/screen thay đổi | WS GW fan-out room |

## **7.5 Cấu trúc thư mục**

call-service/

├── CallService.Api/

│   ├── Controllers/

│   │   ├── CallController.cs

│   │   └── SignalingController.cs  # WebSocket signaling

│   └── Program.cs

├── CallService.Application/

│   ├── Services/

│   │   ├── CallSessionService.cs

│   │   ├── SignalingService.cs

│   │   └── SfuIntegrationService.cs

│   └── EventHandlers/

├── CallService.Domain/

│   ├── Entities/    # CallSessionEntity, CallParticipantEntity

│   └── Enums/       # CallType, CallStatus, ScreenShareTarget

├── CallService.Infrastructure/

│   ├── Persistence/ # EF Core

│   ├── Redis/

│   └── Sfu/         # LiveKit/mediasoup client

└── CallService.sln

## **7.6 Ghi chú triển khai — dùng LiveKit/Agora làm SFU**

Call Service là lớp mỏng: chỉ quản lý call_session (mục 7.3) + gọi API LiveKit/Agora để tạo room và sinh token JWT cho từng participant — KHÔNG tự xây signaling/SFU/TURN riêng. Client dùng LiveKit/Agora SDK để join room bằng token; toàn bộ WebRTC (offer/answer/ICE, simulcast, screen share) do SDK lo. Ràng buộc "chỉ 1 người share màn hình tại 1 thời điểm" là logic nghiệp vụ tự kiểm tra ở Call Service, không phải giới hạn của SFU.
