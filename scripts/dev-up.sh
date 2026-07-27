#!/usr/bin/env bash
# Chạy hạ tầng (Postgres/Mongo/Redis/RabbitMQ) cho môi trường dev.
# Gộp .env.base + .env.dev thành .env (file .env là artifact sinh ra, không phải nguồn thật —
# xem .gitignore) vì docker compose chỉ đọc 1 file cho việc thay thế biến ${...} trong YAML.
set -euo pipefail
cd "$(dirname "$0")/.."

cat .env.base .env.dev > .env

docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

echo "Đợi hạ tầng healthy..."
./scripts/wait-for-healthy.sh chatapp-postgres chatapp-mongo chatapp-redis chatapp-rabbitmq

echo "Hạ tầng dev đã sẵn sàng."
