# Skill: Outbox Pattern

> Áp dụng khi: bất kỳ thao tác ghi DB nào cần publish 1 RabbitMQ event ngay sau đó
> (gần như mọi write nghiệp vụ trong hệ thống này).

## Vấn đề pattern này giải quyết

Nếu code ghi DB xong rồi mới publish RabbitMQ trong 2 bước tách rời, có khoảng hở: ghi DB
thành công nhưng publish thất bại (crash, mất mạng) → event bị mất vĩnh viễn, hệ thống rơi vào
trạng thái không nhất quán (VD: tin nhắn đã lưu nhưng người nhận không bao giờ được báo).

## Cách làm bắt buộc trong toàn hệ thống

> Đổi quyết định (2026-08-09): KHÔNG dùng 1 Go relay worker tách riêng process nữa — mỗi service
> tự publish RabbitMQ bằng chính ngôn ngữ của nó, ngay sau khi transaction nghiệp vụ commit.
> Không chèn code Go vào 1 service không phải Go chỉ để làm việc này.

1. Mỗi service có 1 bảng/collection `outbox_events` riêng trong chính DB của service đó.
2. Khi xử lý 1 request nghiệp vụ, thao tác ghi bảng chính VÀ insert vào `outbox_events` phải
   nằm **trong cùng 1 transaction** (Postgres: cùng DB transaction; MongoDB: cùng document
   hoặc dùng multi-document transaction nếu bắt buộc).
3. Ngay sau khi transaction đó **commit xong** (không phải trong lúc transaction còn mở), service
   tự publish lên RabbitMQ bằng cơ chế "chạy sau commit" của framework đang dùng — Java/Spring:
   `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()` (xem
   `base/OutboxEventPublisher.java` + `base/OutboxDispatcher.java` ở Core Service — publisher
   insert + đăng ký hook, dispatcher là bean riêng thật sự gọi `RabbitTemplate` để hook không bị
   lỗi self-invocation bỏ qua proxy `@Transactional`); .NET dùng
   `TransactionScope`/`IHostedService` tương đương; Go (api-gateway/media-service/realtime-gateway
   nếu sau này cần publish) dùng goroutine sau khi transaction commit. Publish thành công thì
   update `status=PUBLISHED` NGAY trong cùng lần gọi đó (transaction riêng, không phải transaction
   nghiệp vụ gốc — transaction đó đã đóng rồi).
4. Một job chạy định kỳ trong CHÍNH service đó (Java: `@Scheduled`, .NET:
   `IHostedService`/`BackgroundService`, Go: 1 goroutine với `time.Ticker`) quét lại các row còn
   `status=PENDING` — đây là lưới an toàn cho trường hợp bước 3 bỏ lỡ (crash giữa lúc commit và
   publish, RabbitMQ tạm thời không kết nối được), KHÔNG phải cơ chế publish chính. Java: job này
   đặt ở package top-level riêng `scheduler/` (VD `scheduler/OutboxRetryJob.java`), KHÔNG để
   trong `base/` — `base/` chỉ dành cho Entity/Repository/Config/Enum + hạ tầng publish
   (`OutboxEventPublisher`/`OutboxDispatcher`), không phải chỗ chứa job.
5. Payload truyền vào publish (Java: `base/OutboxEventPublisher#publish(exchange, routingKey,
   aggregateId, aggregateType, payload)` — 5 tham số tường minh, KHÔNG gói `aggregateId`/
   `aggregateType` vào 1 interface/wrapper cho payload — với đúng 1 message hiện có trong hệ
   thống thì làm vậy là premature abstraction, thêm interface chỉ đáng làm khi có ít nhất 2-3
   message lặp lại y hệt shape đó) là 1 class riêng đặt ở `base/message/{domain}/`, tên LUÔN kết
   thúc bằng `Message` (VD `QrLoginApprovedMessage`, chỉ chứa field thật sự cần gửi đi trong JSON
   — KHÔNG nhét `user_id`/field nội bộ khác vào đây, những field đó truyền thẳng qua tham số
   `aggregateId`/`aggregateType` của `publish()`). `event_type` không truyền riêng, luôn lấy từ
   `routingKey` (2 giá trị này theo quy ước luôn giống nhau trong hệ thống này).
6. Nếu publish thất bại (cả lần đầu lẫn lần retry của job định kỳ), `retry_count` tăng lên,
   `status` giữ `PENDING` hoặc chuyển `FAILED` sau N lần retry — không bao giờ xoá event khi
   chưa publish thành công.

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
| last_error | TEXT | Lỗi lần publish gần nhất, NULL nếu chưa từng lỗi |
| created_at | TIMESTAMPTZ | Cùng lúc với transaction nghiệp vụ |
| published_at | TIMESTAMPTZ | NULL cho tới khi publish thành công |

## Phía consumer: chống xử lý trùng (deduplication)

Mỗi service consumer có bảng `received_event_dedup` với `UNIQUE(event_id)`. Trước khi xử lý 1
event nhận được, kiểm tra `event_id` đã tồn tại trong bảng này chưa — nếu có, bỏ qua (event đã
xử lý rồi, có thể do RabbitMQ redeliver). Đây là bước bắt buộc vì RabbitMQ chỉ đảm bảo
"at-least-once delivery", không đảm bảo "exactly-once".

## Việc KHÔNG được làm

- Không publish RabbitMQ **trong khi transaction nghiệp vụ còn đang mở** — luôn phải insert vào
  bảng outbox trước, đợi transaction đó commit xong rồi mới publish (dù publish giờ nằm trong
  chính service đó, không phải relay worker riêng nữa — thứ tự "insert trong transaction → commit
  → publish" vẫn bắt buộc y hệt).
- Không xử lý event mà bỏ qua bước kiểm tra dedup, kể cả khi "chắc chắn không bao giờ trùng".
- Không gọi thẳng phương thức publish qua self-invocation (VD 1 method `@Transactional` gọi
  chính nó trong cùng class ở Java/Spring) — bỏ qua proxy AOP, code chạy như không có transaction
  nào cả mà không báo lỗi gì. Đặt logic publish/update-status ở 1 bean/class riêng (xem
  `OutboxDispatcher` — Core Service) rồi gọi qua đó.
- Không chèn code ngôn ngữ khác vào 1 service chỉ để làm phần publish/retry outbox — mỗi service
  tự làm bằng chính ngôn ngữ nó đang viết (xem đổi quyết định ở đầu file).
