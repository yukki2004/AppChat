# Skill: Logging cho message vào/ra (HTTP, gRPC, RabbitMQ, WS) và trong function

> Áp dụng khi: viết handler nhận request (HTTP/WS/gRPC), viết code publish RabbitMQ event
> (outbox + relay worker), viết consumer xử lý event, gọi gRPC ra service khác, hoặc viết
> bất kỳ hàm nghiệp vụ nào (service class method) có thể thành công/thất bại.
> Đọc skill này TRƯỚC KHI thêm log mới — không tự bịa format log riêng cho từng service.

## Vì sao bắt buộc

Hệ thống có 8 service, giao tiếp qua REST/gRPC/RabbitMQ/WS. Khi debug 1 luồng nghiệp vụ (VD:
"tin nhắn gửi xong nhưng người nhận không được push"), phải lần được dấu vết xuyên suốt nhiều
service và nhiều transport khác nhau. Nếu mỗi service tự log 1 kiểu, không có ID chung để nối
các log lại — coi như không debug được production.

## Nguyên tắc cứng, không có ngoại lệ

1. **Mọi log đều là structured JSON** (1 dòng/entry), không log dạng text tự do nối chuỗi.
2. **Phải có 1 ID xuyên suốt toàn bộ chuỗi xử lý của 1 request**, gọi là `request_id`:
   - Sinh ra ở API Gateway (UUID v4, không có thì tự tạo) khi request vào từ client, ghi vào
     header `X-Request-ID`.
   - **Bắt buộc forward** `request_id` này qua mọi lớp: HTTP header giữa Gateway → service đích,
     gRPC metadata khi service A gọi gRPC sang service B, và **field `request_id` trong payload
     JSON của mọi outbox event** (không chỉ có `event_id`).
   - Consumer nhận event phải log lại đúng `request_id` nhận được, không tự sinh `request_id` mới
     — nếu event được publish do 1 quy trình nền (cron, relay retry) không có request gốc, dùng
     `request_id = event_id` của chính nó.
3. **Log tại 4 điểm bắt buộc cho mọi service có publish/consume RabbitMQ** (không được bỏ điểm
   nào trong 4 điểm này):
   - **Nhận request** (HTTP/WS/gRPC vào): log NGAY khi handler bắt đầu xử lý, trước khi động vào
     DB.
   - **Ghi outbox** (event "sắp bắn"): log ngay sau khi transaction insert `outbox_events` commit
     thành công — đây là log "message đã chắc chắn sẽ được bắn", KHÔNG log tại thời điểm publish
     RabbitMQ thật (2 việc này tách rời, xem `outbox-pattern.md`).
   - **Relay publish thành công/thất bại** (message thực sự rời khỏi hệ thống): relay worker log
     khi publish lên RabbitMQ thành công (`status → PUBLISHED`) hoặc thất bại (`retry_count`
     tăng).
   - **Consumer nhận & xử lý xong event** (message đến): log ngay khi consumer nhận được message
     (trước dedup check) VÀ log kết quả xử lý (thành công / bị bỏ qua do trùng / lỗi).
4. **Không log payload nhạy cảm** — không log mật khẩu, TOTP secret, session_id đầy đủ (chỉ log
   4 ký tự cuối hoặc hash), nội dung tin nhắn 1-1 (privacy). Log ID và metadata, không log nội
   dung message trừ khi field đó vốn public (VD: `event_type`, `routing_key`).

## Field bắt buộc trong mỗi log entry

| Field | Bắt buộc khi nào | Ví dụ |
|---|---|---|
| `timestamp` | Luôn luôn, UTC ISO-8601 | `2026-07-26T10:30:00.123Z` |
| `level` | Luôn luôn | `INFO`, `WARN`, `ERROR` |
| `service` | Luôn luôn | `messaging-service` |
| `request_id` | Luôn luôn (xem mục 2) | UUID v4 |
| `event_id` | Khi log liên quan outbox/consume event | UUID v4 |
| `event_type` / `routing_key` | Khi log publish/consume | `message.sent` |
| `direction` | Khi log message vào/ra | `inbound` / `outbound` |
| `aggregate_id` | Khi log liên quan 1 entity cụ thể | `message_id`, `user_id`... |
| `latency_ms` | Khi log kết thúc xử lý (request hoặc consume) | `42` |
| `outcome` | Khi log kết thúc xử lý | `success` / `failed` / `skipped_duplicate` |

## Quy ước `direction` — phân biệt log message vào vs ra

- `direction: "inbound"` — request/event ĐẾN service này: HTTP/WS/gRPC request nhận vào, hoặc
  RabbitMQ event consume được.
- `direction: "outbound"` — request/event RỜI service này: gRPC call đi service khác, hoặc
  RabbitMQ event publish ra (tại bước relay publish, không phải bước ghi outbox).
- Mọi log tại 4 điểm bắt buộc ở trên đều phải gắn đúng 1 trong 2 giá trị này — không bỏ trống,
  không log kiểu chung chung không phân biệt được là log của chiều nào.

## Log level (áp dụng chung cho mọi log — cả log biên và log trong function)

- `DEBUG`: chi tiết nội bộ chỉ cần khi điều tra sâu — giá trị biến trung gian, bước rẽ nhánh
  trong logic phức tạp (VD: "đang check `is_muted`... kết quả: false"), payload đầy đủ của
  request/event. **Tắt ở production theo mặc định**, chỉ bật tạm thời khi cần debug 1 sự cố cụ
  thể — không để `DEBUG` chạy thường trực vì sẽ ngập log server.
- `INFO`: 1 bước nghiệp vụ có ý nghĩa đã xảy ra và kết thúc bình thường — log tại 4 điểm bắt
  buộc (mục "Nguyên tắc cứng" #3), và các cột mốc nghiệp vụ quan trọng khác trong function (VD:
  "tạo user mới thành công", "gửi push thành công", kể cả `skipped_duplicate` vì đây là hành vi
  đúng của dedup, không phải lỗi). Không log `INFO` cho từng dòng code hay từng bước tính toán
  nhỏ — chỉ log input/output/kết quả của 1 đơn vị nghiệp vụ.
- `WARN`: có bất thường nhưng hàm vẫn tự phục hồi hoặc xử lý tiếp được, không cần con người can
  thiệp ngay — retry (relay publish thất bại lần đầu, gRPC timeout nhưng có fallback), circuit
  breaker chuyển trạng thái, dùng giá trị mặc định vì thiếu config không bắt buộc, validation
  input sai nhưng đã có early-return an toàn.
- `ERROR`: hàm KHÔNG hoàn thành được việc nó phải làm và ảnh hưởng tới nghiệp vụ — publish thất
  bại sau khi hết số lần retry cho phép, consumer xử lý event lỗi không rõ nguyên nhân, gRPC call
  thất bại không có fallback và ảnh hưởng trực tiếp tới response trả về client, exception bị bắt
  (catch) mà không xử lý được tiếp.

## Log trong function nghiệp vụ (ngoài 4 điểm bắt buộc ở biên)

Không phải hàm nào cũng cần log — chỉ log trong các hàm thuộc `*Service` (business logic), không
log trong hàm thuần tiện ích/getter/mapper không có side-effect hay quyết định rẽ nhánh quan
trọng.

- Mỗi hàm nghiệp vụ đáng log nên có tối thiểu: 1 log khi **bắt đầu** (level `DEBUG`, kèm tham số
  đầu vào không nhạy cảm) và 1 log khi **kết thúc** (level `INFO` nếu thành công, `WARN`/`ERROR`
  theo mục Log level ở trên nếu có vấn đề) — không log bắt đầu mà thiếu log kết thúc, sẽ không
  biết hàm có chạy xong hay bị treo/crash giữa chừng.
- Khi hàm có rẽ nhánh quan trọng ảnh hưởng tới kết quả nghiệp vụ (VD: "user bị chặn nên không cho
  gửi tin nhắn", "group đã đủ thành viên tối đa"), log lại lý do rẽ nhánh ở `INFO` hoặc `WARN` —
  không chỉ trả lỗi cho caller mà im lặng không log, sẽ khó tái hiện lại được tại sao 1 request
  cụ thể bị từ chối khi tra log sau này.
- Exception/error bắt được trong `try/catch` (hoặc tương đương theo ngôn ngữ) luôn phải log kèm
  stack trace đầy đủ ở `ERROR` tại nơi bắt được lần đầu tiên — không nuốt exception im lặng, và
  không log lại cùng 1 exception nhiều lần ở nhiều lớp gọi nhau (log 1 lần tại chỗ xử lý cuối
  cùng, các lớp trên chỉ cần rethrow).
- Vẫn phải gắn `request_id` (và `event_id` nếu đang xử lý trong luồng consume event) vào MỌI log
  trong function, kể cả log `DEBUG`/`WARN` không nằm ở 4 điểm bắt buộc — nếu không, log đó không
  nối được vào đúng luồng request khi tra cứu.

## Việc KHÔNG được làm

- Không log message payload đầy đủ ở mức `INFO` cho mọi request — chỉ log payload đầy đủ ở
  `DEBUG` (tắt ở production) hoặc khi `ERROR` để phục vụ điều tra sự cố.
- Không tự thêm field log riêng thay thế field bắt buộc ở trên (VD: dùng `traceId` thay
  `request_id`) — phá vỡ khả năng join log giữa các service viết bằng ngôn ngữ khác nhau.
- Không bỏ qua log ở bước "ghi outbox" chỉ vì nghĩ "relay sẽ tự log publish là đủ" — 2 log này
  trả lời 2 câu hỏi khác nhau: "nghiệp vụ có chắc chắn đã ghi nhận ý định bắn event chưa" và
  "event có thực sự đã rời hệ thống chưa". Thiếu 1 trong 2 sẽ không debug được trường hợp outbox
  có nhưng relay chưa kịp publish (service vừa crash).
- Không log `DEBUG` mọi vòng lặp/mọi dòng code — chỉ log ở ranh giới của 1 đơn vị nghiệp vụ (đầu
  vào, đầu ra, rẽ nhánh quan trọng), không biến log thành trace từng câu lệnh.
- Không log `ERROR` cho lỗi nghiệp vụ bình thường mà caller đã xử lý được (VD: user nhập sai mật
  khẩu, validation fail trả 400) — đây là `WARN` hoặc thậm chí `INFO`, không phải `ERROR`. `ERROR`
  chỉ dành cho tình huống hệ thống không hoạt động đúng như kỳ vọng.
