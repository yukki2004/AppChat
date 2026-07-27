# CLAUDE.md — Call Service

> Đọc `/CLAUDE.md` ở root trước.
> **QUAN TRỌNG NHẤT của service này: đây phải là 1 lớp MỎNG. KHÔNG tự xây signaling/SFU/TURN.**

## Vai trò

Quản lý call_session (ai gọi ai, log cuộc gọi, quyền) và tích hợp LiveKit/Agora để họ lo toàn
bộ phần WebRTC thật sự (signaling, SFU, TURN, simulcast).

## Tech stack

.NET 8 / ASP.NET Core. PostgreSQL. Redis cho call session state.

## Giao tiếp

- **gRPC gọi ra**: `notification-service.SendVoIPPush` — dùng gRPC vì cần độ trễ thấp cho
  chuông báo cuộc gọi đến.
- **REST ra ngoài internet**: gọi API LiveKit/Agora để tạo room, sinh token — đây KHÔNG phải
  gRPC nội bộ, là gọi bên thứ 3.
- **RabbitMQ publish** (`call.exchange`): `call.initiated`, `call.answered`, `call.ended`,
  `call.missed`, `call.rejected`, `call.participant_update`.
- **RabbitMQ consume**: không có — Call Service là nguồn phát, Messaging Service mới là bên
  consume `call.ended` để tạo `MESSAGE_CALL_LOG`.

## Chức năng chính

Khởi tạo cuộc gọi 1-1/nhóm (tạo room qua LiveKit/Agora, không tự tạo) · chấp nhận/từ chối/nhỡ ·
kết thúc cuộc gọi · mic/camera toggle · share màn hình (giới hạn 1 người/lúc) · TURN/STUN config
(lấy từ LiveKit/Agora, không tự host) · lịch sử cuộc gọi · quality metrics log.

## Lưu ý khi code — điểm hay bị làm sai nhất trong toàn hệ thống

1. **Không tự implement WebRTC.** Client dùng LiveKit/Agora SDK để join room bằng token do
   Call Service sinh ra — toàn bộ offer/answer/ICE/simulcast do SDK lo. Nếu thấy code đang viết
   liên quan tới `RTCPeerConnection`, ICE candidate, hay tự quản lý SDP — dừng lại, đang đi sai
   hướng.
2. **Ràng buộc "1 người share màn hình/lúc" là business logic của Call Service**, không phải
   giới hạn kỹ thuật SFU — kiểm tra qua webhook LiveKit/Agora hoặc client tự POST báo trước khi
   share, reject nếu đã có người khác đang share.
3. **Call nhóm và call 1-1 dùng chung 1 cơ chế** — chỉ khác số participant join vào room, không
   cần logic riêng biệt cho 2 trường hợp.
4. **Cuộc gọi đến không được bị mute** — Notification Service consume `SendVoIPPush` qua gRPC,
   không qua RabbitMQ, không áp dụng `notification_settings.push_enabled`.
5. Timeout 30s cho trạng thái RINGING → tự chuyển MISSED nếu không ai bắt máy, publish
   `call.missed`.
