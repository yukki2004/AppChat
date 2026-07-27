# Skill: Database-per-Service & Migration

> Áp dụng khi: tạo schema mới, sửa schema, hoặc bất kỳ lúc nào cân nhắc "đọc thẳng DB service
> khác cho tiện".

## Quy tắc cứng, không có ngoại lệ

**Không service nào được kết nối trực tiếp vào database của service khác — kể cả chỉ để đọc
(read-only connection cũng không được phép).** Mọi dữ liệu cần từ service khác phải lấy qua:
- gRPC nếu cần ngay và đồng bộ (xem `service-communication.md`)
- Consume RabbitMQ event rồi lưu bản sao cần thiết vào chính DB của mình (denormalize có chủ
  đích), nếu cần dữ liệu đó thường xuyên và không muốn gọi gRPC mỗi lần

Lý do lưu bản sao thay vì gọi gRPC liên tục: VD `last_message_preview` trong `conversations`
(Messaging Service) là bản sao rút gọn, không phải gọi lại DB gốc mỗi lần hiển thị danh sách hội
thoại.

## Database & migration tool theo từng service

| Service | Database | Migration tool | Vị trí |
|---|---|---|---|
| api-gateway | Không có (Redis cache only) | — | — |
| realtime-gateway | Redis (không schema quan hệ) | Không cần | — |
| core-service | PostgreSQL | Flyway | `src/main/resources/db/migration` |
| messaging-service | MongoDB | Mongock | `src/main/resources/mongock` |
| notification-service | PostgreSQL | EF Core Migrations | `Migrations/` |
| media-service | MongoDB | Migration script Go tự viết, versioned | `internal/migrations` |
| call-service | PostgreSQL | EF Core Migrations | `Migrations/` |
| social-service | PostgreSQL + MongoDB | Flyway (Postgres) + Mongock (Mongo) | `db/migration` + `mongock` |

## Nguyên tắc vận hành migration

1. Migration chạy tự động trong pipeline CI/CD khi deploy — không migrate tay trên production
   trong bất kỳ trường hợp nào.
2. Migration nằm ngay trong thư mục của chính service đó trong monorepo, versioned cùng code —
   không gom vào 1 thư mục `db/` chung cho cả hệ thống.
3. Forward-only: khi cần sửa/huỷ 1 thay đổi schema đã migrate, viết migration MỚI để undo,
   không sửa lại hoặc xoá migration cũ đã chạy trên môi trường nào đó — dữ liệu ghi sau migration
   cũ sẽ mất nếu rollback ngược.
4. Mỗi migration phải chạy được nhiều lần mà không lỗi (idempotent ở mức có thể) hoặc được
   migration tool tự track đã chạy chưa (Flyway/EF Core/Mongock đều tự làm việc này qua bảng
   lịch sử migration riêng — không tự ý xoá bảng lịch sử này).

## Dấu hiệu cảnh báo khi code (agent tự kiểm tra)

Nếu đang viết code trong `service-A` mà thấy mình cần: connection string của `service-B`, import
ORM entity của `service-B`, hoặc JOIN SQL nhắc tới bảng thuộc `service-B` — dừng lại, đây là vi
phạm database-per-service. Giải pháp đúng: thêm gRPC method mới ở `service-B`, hoặc consume thêm
1 RabbitMQ event để đồng bộ bản sao dữ liệu cần thiết.
