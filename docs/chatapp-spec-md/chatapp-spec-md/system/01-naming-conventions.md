# **PHỤ LỤC A – NAMING CONVENTIONS**

Quy ước đặt tên class, struct, interface, biến, hàm trong từng ngôn ngữ. Áp dụng thống nhất toàn dự án.

## **A.1 Java (Spring Boot)**

|  |  |  |
| :-: | :-: | :-: |
| **Loại** | **Convention** | **Ví dụ** |
| **Entity (JPA)** | PascalCase + suffix Entity | UserEntity, FriendshipEntity, MessageEntity |
| **MongoDB Document** | PascalCase + suffix Document | MessageDocument, ConversationDocument |
| **DTO (request)** | PascalCase + suffix Request | CreateUserRequest, LoginRequest, SendMessageRequest |
| **DTO (response)** | Public + PascalCase + DTO | PublicUserDTO, PublicMessageDTO, PublicGroupDTO |
| **Service class** | PascalCase + suffix Service | AuthService, FriendshipService, MessageService |
| **Repository** | PascalCase + suffix Repository | UserRepository, MessageRepository |
| **Controller** | PascalCase + suffix Controller | AuthController, MessageController |
| **gRPC service** | PascalCase + suffix GrpcService | IdentityGrpcService, GroupGrpcService |
| **Enum** | PascalCase (class) + UPPER_SNAKE (values) | enum FriendshipStatus { PENDING, ACCEPTED } |
| **Constant class** | PascalCase + suffix Constants | SessionConstants, MessageConstants |
| **Method** | camelCase | sendMessage(), verifySession(), createGroup() |
| **Field (private)** | camelCase | private String sessionId; private UUID userId; |
| **Field (public record)** | camelCase | record PublicUserDTO(UUID id, String displayName) |
| **Package** | lowercase, dot-separated | com.chatapp.identity.service |
| **Annotation** | @PascalCase (Spring) | @Service, @RestController, @Transactional |

## **A.2 .NET / C# (ASP.NET Core)**

|  |  |  |
| :-: | :-: | :-: |
| **Loại** | **Convention** | **Ví dụ** |
| **Entity (EF Core)** | PascalCase + suffix Entity | NotificationEntity, DeviceTokenEntity |
| **DTO (request)** | PascalCase + suffix Request | RegisterDeviceTokenRequest |
| **DTO (response)** | Public + PascalCase + DTO | PublicNotificationDTO, PublicCallDTO |
| **Service class** | PascalCase + suffix Service | NotificationService, PushDispatcher |
| **Interface** | I + PascalCase | IPushProvider, INotificationRepository |
| **Repository** | PascalCase + suffix Repository | NotificationRepository |
| **Controller** | PascalCase + suffix Controller | NotificationController, CallController |
| **Enum** | PascalCase (type) + PascalCase (values) | enum NotificationType { MessageNew, CallIncoming } |
| **Constant** | static readonly / const PascalCase | public const int MaxRetryCount = 5; |
| **Method** | PascalCase | SendPushAsync(), MarkAsRead(), CreateCallSession() |
| **Property** | PascalCase | public bool IsRead { get; set; } |
| **Variable (local)** | camelCase | var notificationCount = ...; |
| **Field (private)** | _camelCase | private readonly ILogger _logger; |
| **Namespace** | PascalCase.dot.separated | ChatApp.Notification.Application.Services |
| **Record** | PascalCase + DTO/Request/Response | public record PublicNotificationDTO(...) |

## **A.3 Go**

|  |  |  |
| :-: | :-: | :-: |
| **Loại** | **Convention** | **Ví dụ** |
| **Struct (exported)** | PascalCase | type GatewayConfig struct { } |
| **Struct (internal)** | PascalCase (package-private bằng lowercase pkg) | type connectionEntry struct { } |
| **Interface** | PascalCase, thường suffix -er | type PushSender interface { Send() error } |
| **Function (exported)** | PascalCase | func NewPresenceService(r *redis.Client) *PresenceService |
| **Function (internal)** | camelCase | func parseScreenKey(raw string) string |
| **Method** | PascalCase (exported), camelCase (internal) | func (s *Service) GetPresence(id string) |
| **Variable** | camelCase | sessionID, userID, connID |
| **Constant** | PascalCase (exported), camelCase (pkg) | const DefaultTTLSec = 35 |
| **Custom type** | PascalCase | type PresenceStatus string |
| **Enum (iota)** | PascalCase (const block) | const ( Online PresenceStatus = "online" ) |
| **Package** | lowercase single word | package middleware, package handler, package redis |
| **File** | snake_case.go | presence_service.go, cookie_auth.go |
| **Error variable** | err (local), Err+PascalCase (exported) | var ErrSessionNotFound = errors.New(...) |
| **DTO struct** | PascalCase + DTO | type PublicPresenceDTO struct { } |
| **Config struct** | PascalCase + Config | type RedisConfig struct { } |
