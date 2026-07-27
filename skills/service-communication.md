# Skill: Chọn giao thức giao tiếp giữa các service

> Áp dụng khi: thêm bất kỳ lời gọi nào từ service A sang service B, hoặc từ client sang backend.
> Đọc skill này TRƯỚC KHI viết code gọi cross-service — chọn sai giao thức là lỗi kiến trúc,
> không phải chi tiết cài đặt có thể sửa sau dễ dàng.

## Bảng quyết định nhanh

| Tình huống | Giao thức | Vì sao |
|---|---|---|
| Client gọi backend (mọi request từ app/web) | REST qua API Gateway | Client không bao giờ gọi thẳng service nội bộ hay gRPC |
| Client cần nhận cập nhật realtime | WebSocket qua Realtime Gateway | Không polling |
| Service A cần kết quả NGAY để quyết định bước tiếp theo | gRPC | Đồng bộ, blocking, có response mới đi tiếp được |
| Service A cần làm 1 side-effect không ai chờ | RabbitMQ | Bất đồng bộ, publisher không cần biết consumer xử lý xong chưa |
| Cần độ trễ cực thấp nhưng vẫn là side-effect (VD: VoIP push) | gRPC (ngoại lệ) | Xem mục "Ngoại lệ" bên dưới |
| Upload/download file nặng | Client ↔ R2 trực tiếp (presigned URL) | Không qua bất kỳ service nào làm trung gian bytes |
| Service cần gọi API bên thứ 3 (Google OAuth, LiveKit, FCM) | REST ra ngoài internet | Không phải gRPC nội bộ, không phải RabbitMQ |

## Câu hỏi để tự kiểm tra trước khi chọn

1. **"Tôi có cần biết kết quả trước khi làm bước tiếp theo không?"**
   Có → gRPC. Không → RabbitMQ.
   VD: gửi tin nhắn nhóm cần biết user còn trong nhóm không (CheckGroupRole) trước khi cho
   gửi → gRPC. Gửi xong rồi mới báo cho Notification Service đi push → RabbitMQ.

2. **"Nếu request này thất bại giữa chừng, dữ liệu chính có bị sai không?"**
   Có (VD: verify session sai mà vẫn cho qua) → gRPC, xử lý lỗi ngay tại chỗ.
   Không (VD: notification không gửi được thì retry sau cũng không sao) → RabbitMQ.

3. **"Có bao nhiêu consumer cần biết về sự kiện này?"**
   1 consumer, cần response → gRPC.
   Nhiều consumer, không cần response (VD: `message.sent` được Realtime Gateway VÀ
   Notification Service cùng consume) → RabbitMQ, không gọi gRPC lặp lại cho từng consumer.

## Ngoại lệ đáng chú ý: JWKS endpoint (`/.well-known/jwks.json`) dùng HTTP thay vì gRPC

Core Service expose public key để verify JWT qua **HTTP GET JSON chuẩn RFC 7517**, không phải
gRPC — vì đây là convention ngành, mọi thư viện JWT (Go/Java/.NET) đều expect fetch JWKS qua
HTTP sẵn có cơ chế cache/refresh built-in. Đây là ngoại lệ có chủ đích thứ 2 (cùng loại với
SendVoIPPush bên dưới — công nghệ/convention bên ngoài quyết định giao thức, không phải tuỳ ý
chọn REST cho tiện). Chi tiết: `skills/authentication.md`.

## Ngoại lệ đáng chú ý: SendVoIPPush dùng gRPC thay vì RabbitMQ

Cuộc gọi đến cần độ trễ cực thấp (chuông phải reo gần như ngay lập tức), nên Call Service gọi
Notification Service qua **gRPC** thay vì RabbitMQ, dù về bản chất đây là 1 side-effect. Đây là
ngoại lệ có chủ đích, không phải mẫu để áp dụng tuỳ tiện cho các trường hợp "muốn nhanh" khác —
mặc định vẫn luôn ưu tiên RabbitMQ cho side-effect.

## Quy tắc cứng, không có ngoại lệ

- **Không bao giờ** để 1 service gọi REST/HTTP nội bộ sang service khác để lấy dữ liệu —
  dùng gRPC. REST chỉ tồn tại ở lớp API Gateway ↔ client.
- **Không bao giờ** publish RabbitMQ event rồi chờ (poll) kết quả trả về — nếu cần response,
  đó là dấu hiệu phải dùng gRPC, không phải RabbitMQ.
- **Không bao giờ** để backend là trung gian nhận bytes của file lớn — luôn presigned URL
  thẳng tới R2.
- **Không bao giờ** gọi thẳng API LiveKit/Agora/FCM/APNs/R2 từ service không phải chủ sở hữu
  domain đó (chỉ Call Service gọi LiveKit/Agora, chỉ Notification Service gọi FCM/APNs, chỉ
  Media Service gọi R2).
