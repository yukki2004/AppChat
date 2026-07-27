# CLAUDE.md — Media Service

> Đọc `/CLAUDE.md` ở root trước. Service duy nhất được phép gọi Cloudflare R2 API.

## Vai trò

Upload, lưu trữ, streaming file media (ảnh/video/file/sticker). Không xử lý business logic
messaging hay call — chỉ upload và trả CDN URL.

## Tech stack

Go (Fiber). Cloudflare R2 (S3-compatible) + CDN. MongoDB cho tracking upload.

## Giao tiếp

- **RabbitMQ publish** (`media.exchange`): `media.upload_completed`, `media.processing_done`,
  `media.quarantine`.
- Không consume gì — service này chỉ là nguồn phát event, không phụ thuộc service khác.
- Duy nhất trong hệ thống được gọi thẳng Cloudflare R2 API — service khác cần thao tác file
  phải qua Media Service, không tự gọi R2.

## Chức năng chính

Khởi tạo upload session (presigned multipart URL) · client upload chunk thẳng lên R2 (backend
không nhận bytes) · confirm chunk · complete upload → `CompleteMultipartUpload` · streaming
video qua HTTP Range Request (CDN tự hỗ trợ, không cần code riêng) · resize ảnh on-the-fly qua
Cloudflare Image Resizing · generate thumbnail video (FFmpeg worker) · quét virus (ClamAV
worker) · giới hạn kích thước theo loại file · **sticker pack**.

## Lưu ý khi code

- **`context_type` + `context_id`** trên `media_uploads` là cách duy nhất các service khác biết
  file này thuộc về đối tượng nào (`message`, `post`, `story`, `avatar`, `cover`) — LUÔN set 2
  field này khi init upload, đây là "sợi dây" nối ngược về Messaging/Social/Core Service.
- **Không nhầm 2 loại streaming**: streaming lúc UPLOAD (chunk 5MB lên R2, tránh nghẽn băng
  thông server) khác hoàn toàn streaming lúc XEM (HTTP Range Request khi phát video, CDN tự lo,
  không cần code thêm ở service này trừ khi sau này làm HLS đa chất lượng).
- Virus scan và thumbnail generation chạy ASYNC sau khi `status=COMPLETED`, không chặn response
  `upload/complete` — client đã thấy file dùng được trước khi 2 việc này xong.
- GIF không lưu ở đây — chỉ Messaging Service lưu URL Giphy/Tenor trong `link_preview`, gọi qua
  API Gateway để giấu API key (Media Service không liên quan tới GIF).
