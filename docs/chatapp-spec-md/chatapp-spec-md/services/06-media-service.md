# **SERVICE 6 – MEDIA SERVICE**

Đảm nhận toàn bộ upload, lưu trữ, streaming file media (ảnh, video, file đính kèm). Không xử lý business messaging hay call – chỉ upload/trả CDN URL.

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Go (Fiber) |
| **Storage** | Cloudflare R2 (S3-compatible) + Cloudflare CDN |
| **Database** | MongoDB (upload tracking) |
| **Cache** | Redis (presigned URL cache, processing status) |
| **Upload** | Chunked multipart upload trực tiếp client → R2 (bypass backend sau bước 1) |

## **6.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Khởi tạo upload session** | Client gửi {file_name, file_size, mime_type} → server tạo upload_id, presigned multipart URL, trả về cho client. |
| **2** | **Client upload chunk lên R2** | Client dùng presigned URL upload từng chunk (5MB) thẳng lên Cloudflare R2. Backend không nhận bytes. |
| **3** | **Confirm chunk nhận** | Client báo chunk index xong → Media Service update chunks_uploaded counter. |
| **4** | **Hoàn thành upload** | Client báo complete → Media Service gọi R2 CompleteMultipartUpload → update status=COMPLETED, lưu cdn_url. |
| **5** | **Streaming video (HTTP Range)** | CDN phục vụ Range Request (206 Partial Content) cho video. Client dùng HLS/DASH adaptive bitrate. |
| **6** | **Generate thumbnail** | Sau upload video → FFmpeg worker extract frame tại giây 1 → upload thumbnail lên R2 → lưu thumbnail_url. |
| **7** | **Resize ảnh on-the-fly** | Cloudflare Image Resizing: cdn.app.com/img/{key}?w=400&h=400&fit=cover. Không cần pre-generate. |
| **8** | **Xoá file** | Soft delete upload record, schedule R2 delete sau 7 ngày (retention). |
| **9** | **Kiểm tra virus/malware** | Sau upload complete → ClamAV scan async. Nếu infected → mark quarantine, xoá CDN URL. |
| **10** | **Giới hạn file size** | Ảnh: tối đa 25MB. Video: 500MB. File: 100MB. Trả 413 nếu vượt. |
| **11** | **Whitelist MIME type** | Validate mime_type trước khi cấp presigned URL. Reject loại không hỗ trợ. |
| **12** | **Presigned URL download** | Với file private → generate presigned download URL TTL 1 giờ. |
| **13** | **Upload progress WebSocket** | WS push upload progress % về màn hình chat khi mỗi chunk hoàn thành. |
| **14** | **Bulk delete** | Admin endpoint xoá hàng loạt media cũ/vi phạm. |

## **6.2 Enums**

**Enum: UploadStatus**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **PENDING** | Vừa tạo session, chưa upload |
| **UPLOADING** | Đang upload chunks |
| **PROCESSING** | Đang generate thumbnail / scan virus |
| **COMPLETED** | Upload và xử lý xong, CDN URL sẵn sàng |
| **FAILED** | Lỗi upload hoặc scan thất bại |
| **QUARANTINE** | Phát hiện malware |
| **DELETED** | Đã xoá (soft) |

**Enum: MediaType**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **IMAGE** | Ảnh (JPEG, PNG, WebP, GIF) |
| **VIDEO** | Video (MP4, MOV, WebM) |
| **FILE** | File đính kèm (PDF, DOCX, XLSX, ZIP...) |
| **AUDIO** | File âm thanh (MP3, AAC, OGG) |

## **6.3 Database Schema**

### **ð media_uploads (MongoDB)  [MongoDB]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **upload_id** | UUID UNIQUE | NO | client gen | Idempotency key |
| **uploader_id** | UUID | NO | – | Người upload |
| media_type | String | NO | – | Enum MediaType |
| mime_type | String | NO | – | video/mp4, image/jpeg... |
| original_file_name | String | NO | – | Tên file gốc |
| file_size_bytes | Long | NO | – | Tổng kích thước |
| r2_key | String | YES | NULL | Cloudflare R2 object key — format bắt buộc theo `skills/naming-conventions.md` mục 9, bất biến sau khi tạo |
| cdn_url | String | YES | NULL | Public CDN URL |
| thumbnail_url | String | YES | NULL | Thumbnail CDN URL (video/image) |
| duration_sec | Int | YES | NULL | Thời lượng video/audio |
| width_px | Int | YES | NULL | Chiều rộng (px) |
| height_px | Int | YES | NULL | Chiều cao (px) |
| upload_status | String | NO | PENDING | Enum UploadStatus |
| **multipart_upload_id** | String | YES | NULL | R2 multipart upload ID |
| total_chunks | Int | YES | NULL | Tổng số chunk |
| chunks_uploaded | Int | NO | 0 | Số chunk đã nhận |
| virus_scan_status | String | NO | PENDING | PENDING / CLEAN / INFECTED |
| virus_scan_at | Date (UTC) | YES | NULL | Thời điểm scan |
| context_type | String | YES | NULL | message / post / story / avatar / cover / sticker — quyết định nhánh format `r2_key` (mục 9 naming-conventions.md); riêng `message` có thêm `conversation_id`/`conversation_type` trong key vì gắn với `conversations` (DIRECT/GROUP/SELF, xem `04-messaging-service.md` mục 4.12/4.13) |
| **context_id** | String | YES | NULL | ID đối tượng liên quan |
| is_deleted | Boolean | NO | false | Soft delete |
| deleted_at | Date (UTC) | YES | NULL | UTC |
| created_at | Date (UTC) | NO | now() | UTC |
| completed_at | Date (UTC) | YES | NULL | UTC – hoàn thành |

### **ð upload_chunks (MongoDB)  [MongoDB – Track từng chunk]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **_id** | ObjectId | NO | auto | PK |
| **upload_id** | UUID | NO | – | Ref → media_uploads, index |
| chunk_index | Int | NO | – | Thứ tự chunk (0-based) |
| etag | String | YES | NULL | R2 ETag trả về sau upload |
| size_bytes | Long | NO | – | Kích thước chunk |
| uploaded_at | Date (UTC) | NO | now() | UTC |

## **6.4 RabbitMQ Events**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| media.upload_completed | media.exchange | media.upload_completed | Upload hoàn thành, CDN URL sẵn sàng | Messaging / Social (update message media_url) |
| media.processing_done | media.exchange | media.processing_done | Thumbnail generate xong | Messaging (update thumbnail_url) |
| media.quarantine | media.exchange | media.quarantine | Phát hiện virus | Messaging (xoá message), Notification (cảnh báo admin) |

## **6.5 Cấu trúc thư mục**

media-service/

├── cmd/main.go

├── internal/

│   ├── handler/

│   │   ├── upload_handler.go

│   │   └── admin_handler.go

│   ├── service/

│   │   ├── upload_service.go

│   │   ├── thumbnail_service.go   # FFmpeg worker

│   │   └── virus_scan_service.go

│   ├── r2/                        # Cloudflare R2 client

│   ├── repository/                # MongoDB upload repo

│   └── event/                     # RabbitMQ publisher

├── pkg/

│   ├── ffmpeg/                    # FFmpeg wrapper

│   └── clamav/                    # ClamAV client

└── go.mod

## **6.6 Sticker Pack**

Thêm STICKER vào Enum: MediaType (mục 6.2). Collection mới: sticker_packs [MongoDB]

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| _id | ObjectId | NO | auto | PK |
| name | String | NO | – | Tên pack |
| thumbnail_url | String | NO | – | CDN URL |
| sticker_ids | Array<ObjectId> | NO | [] | Ref → media_uploads (mỗi sticker là 1 upload media_type=STICKER) |
| is_active | Boolean | NO | true | Admin có thể ẩn pack |
| created_at | Date (UTC) | NO | now() | – |

GIF: không lưu trữ trong hệ thống — Messaging Service chỉ lưu URL từ Giphy/Tenor trong field link_preview (mục 4.4). Emoji reaction: không đổi, vẫn là string Unicode thuần trong field reactions.
