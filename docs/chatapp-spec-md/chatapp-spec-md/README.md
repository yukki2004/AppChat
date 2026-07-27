# Chat App — Spec Reference (Markdown)

Bộ tài liệu này tách từ bản spec đầy đủ (ChatApp_Architecture_Spec.docx, 71 trang) thành từng
file markdown nhỏ, mỗi file 1 service hoặc 1 chủ đề convention chung, để dễ nạp làm ngữ cảnh cho
AI coding agent (Claude Code) mà không cần đọc toàn bộ 71 trang mỗi lần.

## system/ — Quy ước & luồng chung toàn hệ thống

- `00-overview.md` — Tổng quan hệ thống, kiến trúc tổng thể, giao tiếp giữa service
- `01-naming-conventions.md` — Naming convention Java/.NET/Go
- `02-rabbitmq-exchange-map.md` — Bảng exchange/routing key RabbitMQ
- `03-grpc-service-catalog.md` — Danh mục toàn bộ gRPC method nội bộ
- `04-cache-key-glossary.md` — Bảng Redis cache key
- `05-cookie-auth-flow.md` — Luồng cookie-based session chi tiết
- `06-business-flows.md` — Luồng nghiệp vụ đầy đủ (login, gửi tin, call, upload file...) kèm
  giao thức REST/gRPC/RabbitMQ/WS cho từng bước
- `07-repo-and-migration.md` — Cấu trúc repo backend/frontend, database-per-service, migration

## services/ — Chi tiết từng service (schema, enum, chức năng)

- `01-api-gateway.md`
- `02-realtime-gateway.md`
- `03-core-service.md`
- `04-messaging-service.md`
- `05-notification-service.md`
- `06-media-service.md`
- `07-call-service.md`
- `08-social-service.md`

## Cách dùng gợi ý

Khi làm việc trên 1 service cụ thể, nạp cho AI: `system/00-overview.md` +
`services/<service>.md` + các file `system/` liên quan tới việc đang làm (VD: sửa naming thì
nạp thêm `01-naming-conventions.md`). Không cần nạp cả 16 file cùng lúc.
