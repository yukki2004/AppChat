# **SERVICE 2 – REALTIME GATEWAY**

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Go (gorilla/websocket hoặc nhúng Fiber WebSocket) |
| **Role** | Quản lý toàn bộ WebSocket connection. Mỗi màn hình client duy trì đúng 1 WS connection. |
| **Database** | Không có DB – stateless connection manager |
| **Cache** | Redis Pub/Sub nhận event từ các service, fan-out đến đúng connection |
| **Auth** | Verify cookie access_token (JWT) tại chỗ bằng public key khi handshake WS — không gRPC. Chi tiết: `system/05-cookie-auth-flow.md` E.8. |

## **2.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **WS Handshake & Auth** | Upgrade HTTP→WS, đọc cookie access_token (JWT), verify chữ ký RS256 tại chỗ + check `exp` + Redis revoke → lấy user_id từ claim `sub`. Reject nếu invalid. Không re-verify theo TTL 15p giữa phiên — dựa vào event `user.blocked`/logout-all để force-disconnect. |
| **2** | **1 Connection / Screen** | Mỗi client gửi frame {"action":"screen_register","screen":"chat:convId"} khi vào màn hình. GW lưu map screen→connID. Chuyển màn hình → cập nhật subscription, không tạo WS mới. |
| **3** | **Connection Registry** | Lưu Redis: cache:ws:user:{user_id} → Set{connID}. cache:ws:screen:{userID}:{screen} → connID. |
| **4** | **Redis Pub/Sub Fan-out** | Subscribe Redis channel per user_id. Nhận event từ services, route đến connID đúng màn hình. |
| **5** | **Heartbeat / Ping-Pong** | Server gửi ping mỗi 20s. Client phải pong trong 5s. Nếu không → close conn, cleanup registry. |
| **6** | **Reconnection State** | Khi client reconnect gửi last_event_id → WS GW replay các event chưa nhận từ Redis Stream. |
| **7** | **Broadcast to Room** | event cần gửi đến tất cả members của conversation → GW lookup cache:ws:room:{convId} → fan-out. |
| **8** | **Presence Signal** | Khi conn open → publish presence.online. Khi conn close → publish presence.offline đến Presence Service. |
| **9** | **Rate Limit WS Frame** | Giới hạn 60 frame/phút per connection. Vượt → close conn 1008. |
| **10** | **Graceful Shutdown** | Drain connections, gửi close frame, flush Redis cleanup trước khi pod terminate. |

## **2.2 WS Frame Protocol**

### **Client → Server**

{ "action": "screen_register", "screen": "chat:abc123", "last_event_id": "evt_xyz" }

{ "action": "typing_start",    "conversation_id": "abc123" }

{ "action": "typing_stop",     "conversation_id": "abc123" }

{ "action": "ping" }

### **Server → Client**

{ "event": "message.new",      "screen": "chat:abc123", "payload": { ...PublicMessageDTO } }

{ "event": "presence.update",  "payload": { "user_id":"...", "status":"online" } }

{ "event": "typing.start",     "payload": { "user_id":"...", "conversation_id":"..." } }

{ "event": "notification.new", "payload": { ...PublicNotificationDTO } }

{ "event": "call.incoming",    "payload": { ...PublicCallDTO } }

## **2.3 Naming Convention – Go (same rules as Gateway)**

type ConnectionRegistry struct { ... }

type ScreenSubscription struct { ConnID, UserID, Screen string }

func (r *ConnectionRegistry) Register(connID, userID, screen string)

## **2.4 Cấu trúc thư mục**

ws-gateway/

├── cmd/main.go

├── internal/

│   ├── hub/            # ConnectionHub, Room management

│   ├── handler/        # WS upgrade, frame parser

│   ├── pubsub/         # Redis subscriber, fan-out

│   ├── presence/       # Signal online/offline

│   └── auth/           # gRPC identity client

├── pkg/logger/

├── Dockerfile

└── go.mod

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Go (Fiber) |
| **Database** | Redis (primary store) – không có SQL DB |
| **Giao tiếp** | Nhận event từ RabbitMQ (user.block_set, friend.accepted). WS GW signal online/offline. gRPC exposed GetPresence, GetPrivacySettings. |
| **Realtime** | Publish presence.update vào Redis Pub/Sub → WS GW fan-out |

## **2.5 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Đánh dấu Online** | WS GW publish signal khi user connect WS → set cache:presence:{id} status=online, TTL 35s. |
| **2** | **Đánh dấu Offline** | WS GW publish signal khi disconnect → set status=offline, ghi last_seen_at. |
| **3** | **Trạng thái Away** | Nếu không có WS frame trong 5 phút → set status=away. |
| **4** | **Heartbeat TTL refresh** | WS GW ping mỗi 20s → Presence renew TTL 35s. Nếu miss → tự động offline. |
| **5** | **Ẩn trạng thái vĩnh viễn** | OnlineStatusMode=HIDDEN → luôn trả offline cho người khác. |
| **6** | **Ẩn theo khung giờ** | Cron mỗi 1 phút check schedule_hidden_from/to (gRPC Identity) → set/remove cache:online:hidden:{id}. |
| **7** | **Trả trạng thái theo privacy** | GetPresence kiểm tra: blocked? hidden? scheduled? → trả về đúng status. |
| **8** | **Typing Indicator Start** | WS GW forward typing_start frame → set cache:typing:{convId}:{userId} TTL 3s → publish presence.typing_start. |
| **9** | **Typing Indicator Stop** | Gửi stop frame hoặc TTL expire → publish presence.typing_stop. |
| **10** | **Hiển thị danh sách online bạn bè** | GetPresence(user_ids[]) → batch Redis GET → filter theo privacy → trả PublicPresenceDTO[]. |
| **11** | **Last seen** | Khi offline → lưu last_seen_at vào Redis String (không expire). |
| **12** | **Chỉ bạn bè thấy nhau** | GetPresence filter: nếu không phải bạn bè → trả OFFLINE. |

## **2.6 Redis Key Schema**

### **ð Redis Keys  [Redis (tất cả key bắt đầu cache:)]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| cache:presence:{user_id} | String JSON | 35s | { status, last_seen_at: UTC ISO } |   |
| cache:presence:last_seen:{user_id} | String | – | UTC ISO string – không expire |   |
| cache:online:hidden:{user_id} | String "1" | dynamic | Set khi trong khung giờ ẩn trạng thái |   |
| cache:typing:{conversation_id}:{user_id} | String "1" | 3s | Typing indicator – auto expire |   |
| cache:ws:user:{user_id} | Set | session | Tập connID WS đang kết nối |   |
| cache:ws:screen:{user_id}:{screen} | String | session | connID giữ màn hình này |   |
| cache:ws:room:{conversation_id} | Set | session | connID của members trong conversation |   |

## **2.7 RabbitMQ Events publish**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| presence.online | presence.exchange | presence.online | User connect WS | WS GW → push đến bạn bè |
| presence.offline | presence.exchange | presence.offline | User disconnect WS | WS GW → push last_seen |
| presence.away | presence.exchange | presence.away | Không tương tác 5 phút | WS GW → update status |
| presence.typing_start | presence.exchange | presence.typing_start | Gõ trong chat | WS GW fan-out conversation |
| presence.typing_stop | presence.exchange | presence.typing_stop | Ngừng gõ/TTL expire | WS GW fan-out |

## **2.8 Naming Convention – Go**

type PresenceStatus string

const (

    PresenceOnline  PresenceStatus = "online"

    PresenceAway    PresenceStatus = "away"

    PresenceOffline PresenceStatus = "offline"

)

type PresenceRecord struct { Status PresenceStatus; LastSeenAt time.Time }

type PresenceService struct { redis *redis.Client; grpc IdentityGrpcClient }

## **2.9 Cấu trúc thư mục**

presence-service/

├── cmd/main.go

├── internal/

│   ├── handler/      # HTTP + gRPC handlers

│   ├── service/      # PresenceService, TypingService

│   ├── scheduler/    # ScheduledHideChecker (cron)

│   ├── redis/        # Redis client wrapper

│   └── pubsub/       # RabbitMQ publisher

├── proto/            # presence.proto

└── go.mod
