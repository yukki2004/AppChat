# Chạy ứng dụng — hướng dẫn tổng hợp

File này gộp toàn bộ các bước để chạy được luồng auth/2FA end-to-end trên máy dev. Chi tiết
riêng từng mảng (hạ tầng Docker, quy ước code từng service) vẫn nằm ở `README.md` (hạ tầng) và
`services/<tên-service>/CLAUDE.md` (từng service) — file này chỉ nối các bước lại theo đúng thứ
tự cần làm.

## Bước 1 — Hạ tầng (Postgres/Mongo/Redis/RabbitMQ)

```bash
./scripts/dev-up.sh
```

Xem `README.md` để biết chi tiết cổng dùng (Postgres 5433, Redis 6380 — không phải mặc định).

## Bước 2 — Chạy service cần test

```bash
./scripts/run-service.sh                 # hiện menu chọn service
./scripts/run-service.sh core-service     # hoặc gõ thẳng tên, khỏi qua menu
./scripts/run-service.sh api-gateway
```

Script tự gộp `.env.base`+`.env.dev` rồi export đúng biến môi trường cho từng service — không
cần tự export tay `POSTGRES_PORT`/`CORE_DB_PASSWORD`/`APP_REDIS_PORT`... như trước.

Muốn test luồng auth/2FA đầy đủ (qua test-client, xem Bước 3) thì cần chạy tối thiểu **2**
service cùng lúc, ở 2 terminal riêng:

```bash
./scripts/run-service.sh core-service     # terminal 1 — port 8090
./scripts/run-service.sh api-gateway      # terminal 2 — port 8080
```

### GeoIP (tuỳ chọn, không bắt buộc để chạy được)

`core-service` resolve `login_country`/`login_city` từ IP lúc login bằng file MaxMind
GeoLite2-City `.mmdb` tự tải (không có sẵn trong repo — vài chục MB, phải đăng ký tài khoản
MaxMind free để tải, xem `docs/chatapp-spec-md/chatapp-spec-md/system/05-cookie-auth-flow.md`
mục E.10). Thiếu file này **không** làm service lỗi — chỉ geo lookup luôn trả rỗng.

Nếu muốn bật: tải `GeoLite2-City.mmdb` (chọn đúng bản binary `.mmdb`, không phải bản
`CSV Format`), đặt vào `services/core-service/geoip/GeoLite2-City.mmdb` — `run-service.sh` tự
trỏ `GEOIP_MMDB_PATH` vào đúng chỗ này, không cần cấu hình thêm. Đặt chỗ khác thì
`export GEOIP_MMDB_PATH=/đường/dẫn/khác.mmdb` trước khi gọi script.

## Bước 3 — Test-client (trang HTML test tay luồng auth/2FA)

```bash
cd test-client
python -m http.server 5500
```

Mở `http://localhost:5500` — **không** double-click mở `index.html` trực tiếp (`file://`
origin sẽ không khớp CORS allowlist của `api-gateway` và trình duyệt sẽ không gửi cookie đúng
cách). Cổng **5500 là bắt buộc, cố định** — `api-gateway` chỉ cho phép đúng origin này trong
CORS (`cmd/main.go`, `AllowOrigins`), đổi cổng khác sẽ bị CORS chặn.

Trang test gọi `api-gateway` ở `http://localhost:8080` với `fetch(..., { credentials: 'include' })`
để cookie HttpOnly (`access_token`/`refresh_token`/`pre_auth_token`) được trình duyệt lưu và gửi
lại đúng như 1 client thật.

## Thứ tự test luồng cơ bản

1. Register → Login (nhận `access_token`/`refresh_token`, chưa bật 2FA thì login xong luôn).
2. Bật 2FA (TOTP setup/confirm) — cần `access_token` hợp lệ, TTL chỉ 15 phút nên nếu để lâu
   giữa các bước phải login lại.
3. Logout, login lại → lần này sẽ vào luồng challenge/submit 2FA (`/auth/login/2fa/challenge`
   rồi `/auth/login/2fa`) thay vì nhận token ngay.

## Lỗi hay gặp

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Request treo mãi, không có response | `core-service` chưa chạy, hoặc `api-gateway` trỏ sai `CoreServiceURL` |
| 401 ngay cả khi vừa login xong | `access_token` hết hạn (TTL 15 phút) giữa các bước test — login lại |
| Cookie không được gửi kèm | Mở test-client bằng `file://` thay vì `http://localhost:5500`, hoặc chạy ở cổng khác 5500 |
| 502 Bad Gateway | `core-service` không phản hồi được (crash, sai port, DB chưa kết nối) |
| `core-service` không kết nối được DB | Hạ tầng (`dev-up.sh`) chưa chạy hoặc chưa healthy — check `docker ps` |
| Log warning "No app.geoip.mmdb-path configured" | Bình thường nếu chưa tải file `.mmdb` — không phải lỗi, geo lookup chỉ trả rỗng |

## Service chưa test được qua luồng này

`messaging-service`, `social-service`, `call-service`, `notification-service`,
`realtime-gateway`, `media-service` — có thể khởi động qua `run-service.sh` nhưng chưa có
route/luồng nghiệp vụ nào implement để test qua test-client (chỉ auth/2FA của `core-service` +
`api-gateway` đã code xong tính tới thời điểm này).
