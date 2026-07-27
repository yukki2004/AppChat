# Skill: Outbox Pattern

> Áp dụng khi: bất kỳ thao tác ghi DB nào cần publish 1 RabbitMQ event ngay sau đó
> (gần như mọi write nghiệp vụ trong hệ thống này).

## Vấn đề pattern này giải quyết

Nếu code ghi DB xong rồi mới publish RabbitMQ trong 2 bước tách rời, có khoảng hở: ghi DB
thành công nhưng publish thất bại (crash, mất mạng) → event bị mất vĩnh viễn, hệ thống rơi vào
trạng thái không nhất quán (VD: tin nhắn đã lưu nhưng người nhận không bao giờ được báo).

## Cách làm bắt buộc trong toàn hệ thống

1. Mỗi service có 1 bảng/collection `outbox_events` riêng trong chính DB của service đó.
2. Khi xử lý 1 request nghiệp vụ, thao tác ghi bảng chính VÀ insert vào `outbox_events` phải
   nằm **trong cùng 1 transaction** (Postgres: cùng DB transaction; MongoDB: cùng document
   hoặc dùng multi-document transaction nếu bắt buộc).
3. Một **Go relay worker** riêng (chạy nền, tách khỏi luồng xử lý request) liên tục poll bảng
   `outbox_events` theo `status=PENDING`, publish lên RabbitMQ, rồi update `status=PUBLISHED`.
4. Nếu publish thất bại, `retry_count` tăng lên, `status` giữ `PENDING` hoặc chuyển `FAILED`
   sau N lần retry — không bao giờ xoá event khi chưa publish thành công.

## Cấu trúc bảng outbox_events (áp dụng mọi service, field giống nhau)

| Column | Type | Mô tả |
|---|---|---|
| id | UUID | PK |
| event_id | UUID UNIQUE | Idempotency key, publisher tạo |
| event_type | VARCHAR(100) | VD: `user.registered` |
| aggregate_id | UUID | ID đối tượng liên quan (user_id, message_id...) |
| aggregate_type | VARCHAR(50) | VD: `User`, `Message` |
| payload | JSONB / Object | Nội dung đầy đủ event |
| exchange | VARCHAR(100) | RabbitMQ exchange đích |
| routing_key | VARCHAR(100) | Xem `naming-conventions.md` |
| status | VARCHAR(20) | PENDING / PUBLISHED / FAILED |
| retry_count | INT | Số lần retry |
| created_at | TIMESTAMPTZ | Cùng lúc với transaction nghiệp vụ |
| published_at | TIMESTAMPTZ | NULL cho tới khi publish thành công |

## Phía consumer: chống xử lý trùng (deduplication)

Mỗi service consumer có bảng `received_event_dedup` với `UNIQUE(event_id)`. Trước khi xử lý 1
event nhận được, kiểm tra `event_id` đã tồn tại trong bảng này chưa — nếu có, bỏ qua (event đã
xử lý rồi, có thể do RabbitMQ redeliver). Đây là bước bắt buộc vì RabbitMQ chỉ đảm bảo
"at-least-once delivery", không đảm bảo "exactly-once".

## Việc KHÔNG được làm

- Không publish trực tiếp trong code xử lý request (gọi thẳng RabbitMQ client ngay trong
  transaction) — luôn phải qua bảng outbox + relay worker.
- Không xử lý event mà bỏ qua bước kiểm tra dedup, kể cả khi "chắc chắn không bao giờ trùng".
- Không để relay worker và request-handling code chạy chung 1 transaction — relay worker luôn
  là tiến trình/goroutine độc lập, đọc outbox sau khi transaction gốc đã commit.
