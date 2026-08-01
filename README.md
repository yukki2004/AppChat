# ChatApp Backend — Hạ tầng (Infra)

Hướng dẫn chạy hạ tầng dùng chung cho toàn bộ 8 service: **PostgreSQL, MongoDB, Redis,
RabbitMQ**. File này KHÔNG nói về cách build/chạy từng service (Go/Java/.NET) — xem
`services/<tên-service>/CLAUDE.md` cho việc đó.

## Yêu cầu

- Docker + Docker Compose đã cài và đang chạy.
- Bash (Git Bash trên Windows là đủ) để chạy các script trong `scripts/`.

## Chạy hạ tầng (dev/local)

```bash
./scripts/dev-up.sh
```

Script này tự làm 3 việc:
1. Gộp `.env.base` (giá trị chung, không nhạy cảm) + `.env.dev` (password yếu, chỉ dùng
   local) thành file `.env` — Docker Compose đọc file này để thay `${...}` trong YAML.
2. `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`
3. Đợi tới khi Postgres/Mongo/Redis/RabbitMQ đều "healthy" mới thoát (`wait-for-healthy.sh`).

Sau khi chạy xong, hạ tầng lắng nghe trên host tại các cổng sau (đã cố tình đổi khỏi cổng mặc
định vì máy dev thường đã có sẵn service khác chiếm cổng chuẩn):

| Service | Cổng mặc định | Cổng dùng ở đây | User/Pass (dev) |
|---|---|---|---|
| PostgreSQL | 5432 | **5433** | `postgres` / `postgres_dev_pw` (superuser) |
| MongoDB | 27017 | 27017 | `root` / `mongo_dev_pw` (superuser) |
| Redis | 6379 | **6380** | (không password ở dev) |
| RabbitMQ (AMQP) | 5672 | **5673** | `chatapp_dev` / `rabbitmq_dev_pw` |
| RabbitMQ Management UI | 15672 | **15673** | http://localhost:15673, cùng user/pass AMQP |

Mỗi database (Postgres/Mongo) chứa nhiều schema/user riêng theo từng service (`core_db`,
`notification_db`, `call_db`, `social_db`, `messaging_db`, `media_db`...) — được tạo tự động
lúc container khởi tạo lần đầu (`infra/postgres/init/`, `infra/mongo/init/`). RabbitMQ tự khai
báo sẵn 8 exchange (`user.exchange`, `chat.exchange`...) qua container `rabbitmq-setup`
(`infra/rabbitmq/declare-exchanges.sh`), chạy 1 lần rồi thoát — không phải service chạy nền.

## Thêm database mới cho volume cũ

Nếu bạn thêm 1 service mới cần database mới, nhưng KHÔNG muốn xoá volume Postgres cũ (mất hết
data hiện có) — script init chỉ chạy lúc volume rỗng lần đầu. Dùng script bootstrap idempotent
thay vào đó:

```bash
./scripts/db-bootstrap.sh
```

Chạy lại bao nhiêu lần cũng an toàn — chỉ `CREATE ROLE`/`CREATE DATABASE` cho cái còn thiếu.

## Dừng hạ tầng

```bash
./scripts/dev-down.sh        # dừng container, GIỮ NGUYÊN data (volume)
./scripts/dev-down.sh -v     # dừng + xoá luôn volume — MẤT SẠCH DATA, cẩn thận
```

## Môi trường staging/production

`docker-compose.stg.yml` / `docker-compose.prod.yml` là override tương ứng — dùng
`.env.stg.example` / `.env.prod.example` làm mẫu (copy thành `.env.stg`/`.env.prod` thật, điền
password thật, KHÔNG commit file đã điền). Production override không map port nào ra host —
service app kết nối qua tên container trong network nội bộ `chatapp-infra`.
