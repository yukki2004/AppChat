# **PHỤ LỤC G – REPOSITORY STRUCTURE & DATABASE MIGRATION**

### **G.1 Backend Monorepo**

Toàn bộ 8 service backend (Go/Java/.NET) nằm chung 1 repo — lý do: gRPC contract (proto/) phải thay đổi atomic cùng với các service gọi tới nó trong cùng 1 PR, tránh lệch version giữa các repo riêng lẻ.

chatapp-backend/

├── proto/                          Hợp đồng gRPC dùng chung, quản lý bằng buf (buf.build)

│   ├── identity/v1/identity.proto

│   ├── group/v1/group.proto

│   ├── messaging/v1/messaging.proto

│   └── buf.yaml, buf.gen.yaml       Lint + generate code cho cả Go/Java/.NET từ 1 nguồn

├── services/

│   ├── api-gateway/                 Go

│   ├── realtime-gateway/            Go

│   ├── core-service/                Java

│   ├── messaging-service/           Java

│   ├── social-service/              Java

│   ├── notification-service/        .NET

│   ├── media-service/               Go

│   └── call-service/                .NET

├── docker-compose.yml               Chạy cả 8 service + Postgres/Mongo/Redis/RabbitMQ bằng 1 lệnh

└── .github/workflows/               CI dùng path-filter — chỉ build/test service có file thay đổi

### **G.2 Frontend Repo (tách riêng khỏi backend)**

Tách riêng vì frontend không dùng gRPC (chỉ REST + WS qua API Gateway), chu kỳ release khác hẳn backend (app store review), và tooling hoàn toàn khác.

chatapp-frontend/

├── apps/

│   ├── mobile/                      React Native — app chính iOS + Android

│   └── web/                         (optional, chỉ thêm khi thực sự cần bản web)

├── packages/

│   ├── api-client/                  REST + WS client gọi API Gateway, dùng chung mobile/web

│   ├── types/                       TypeScript type khớp PublicUserDTO, PublicMessageDTO...

│   ├── ui/                          Design system dùng chung (nếu dùng React Native Web)

│   └── utils/                       Timezone convert UTC→local, formatter...

├── package.json                     Root workspace (pnpm/yarn workspaces hoặc Turborepo)

└── .github/workflows/

*Thay thế bằng Flutter (lib/features/, lib/core/, lib/shared/) nếu không chọn React Native — cấu trúc workspace ở trên không cần thiết vì Flutter đã cross-platform trong 1 app.*

### **G.3 Nguyên tắc Database-per-Service (bắt buộc)**

**Mỗi service sở hữu 1 database riêng, KHÔNG service nào được kết nối trực tiếp vào DB của service khác — mọi truy cập chéo phải qua gRPC hoặc RabbitMQ event, đúng nguyên tắc outbox pattern đã áp dụng xuyên suốt spec này.**

|  |  |  |  |
| :-: | :-: | :-: | :-: |
| **Service** | **Database** | **Migration tool** | **Vị trí migration script** |
| API Gateway | Không có (stateless, chỉ dùng Redis cache) | – | – |
| Realtime Gateway | Redis (không có schema quan hệ) | Không cần migration | – |
| Core Service | PostgreSQL | Flyway (chuẩn Java) | services/core-service/src/main/resources/db/migration |
| Messaging Service | MongoDB | Mongock (thư viện migration cho Mongo + Java) | services/messaging-service/src/main/resources/mongock |
| Notification Service | PostgreSQL | EF Core Migrations (chuẩn .NET) | services/notification-service/Migrations |
| Media Service | MongoDB | Migration script Go tự viết (versioned, chạy tuần tự khi service khởi động) | services/media-service/internal/migrations |
| Call Service | PostgreSQL | EF Core Migrations | services/call-service/Migrations |
| Social Service | PostgreSQL + MongoDB | Flyway (phần Postgres) + Mongock (phần Mongo) | services/social-service/.../db/migration + mongock |

Quy tắc vận hành migration: (1) Migration chạy tự động trong pipeline CI/CD khi deploy, không migrate tay trên production. (2) Mỗi service tự quản lý migration của chính nó, versioned ngay trong thư mục service đó trong monorepo — không gom chung 1 thư mục "db/" cho cả hệ thống. (3) Migration theo hướng forward-only (không viết down
