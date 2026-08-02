#!/usr/bin/env bash
# Chạy 1 service ngoài Docker (Java/Go/.NET chạy trực tiếp trên máy dev, KHÔNG phải container)
# với đầy đủ biến môi trường DB/Redis/RabbitMQ/mail đã wire sẵn — tránh phải tự export tay từng
# biến trước mỗi lần chạy.
#
# Yêu cầu: hạ tầng đã chạy (./scripts/dev-up.sh trước).
#
# Cách dùng:
#   ./scripts/run-service.sh                 # hiện menu chọn service
#   ./scripts/run-service.sh core-service     # chạy thẳng, khỏi qua menu
set -euo pipefail
cd "$(dirname "$0")/.."

# Gộp .env.base (giá trị chung) + .env.dev (password yếu, chỉ local) rồi export hết vào môi
# trường tiến trình con — mỗi service (Spring Boot/.NET/Go) đọc đúng biến nó cần, không phải
# lọc trước theo từng service vì thừa biến không gây hại (Spring bỏ qua biến lạ).
set -a
# shellcheck disable=SC1091
source .env.base
# shellcheck disable=SC1091
source .env.dev
set +a

# File .mmdb tải riêng từng máy (xem docs/.../system/05-cookie-auth-flow.md mục E.10), không
# nằm trong .env.dev vì đường dẫn khác nhau tuỳ máy — mặc định trỏ vào chỗ ta hay đặt file này,
# override bằng cách export GEOIP_MMDB_PATH trước khi gọi script nếu bạn để chỗ khác.
export GEOIP_MMDB_PATH="${GEOIP_MMDB_PATH:-$(pwd)/services/core-service/geoip/GeoLite2-City.mmdb}"

# 3 service Go (api-gateway/realtime-gateway/media-service) đọc config qua Viper với
# SetEnvPrefix("APP") (xem internal/config/config.go mỗi service) — biến phải có tiền tố APP_,
# KHÔNG dùng chung tên trơn như REDIS_PORT mà Java/.NET đọc trực tiếp. Alias lại ở đây để 1 lần
# .env.dev là đủ, không phải định nghĩa 2 biến trùng giá trị cho 2 convention khác nhau.
export APP_REDIS_HOST="${REDIS_HOST}"
export APP_REDIS_PORT="${REDIS_PORT}"
export APP_REDIS_PASSWORD="${REDIS_PASSWORD:-}"

SERVICES=(api-gateway realtime-gateway core-service messaging-service notification-service media-service call-service social-service)

choice="${1:-}"
if [[ -z "${choice}" ]]; then
  echo "Chọn service muốn chạy:"
  select choice in "${SERVICES[@]}"; do
    [[ -n "${choice:-}" ]] && break
  done
fi

case "${choice}" in
  core-service|messaging-service|social-service)
    echo "Chạy ${choice} (Java/Spring Boot)..."
    cd "services/${choice}"
    ./mvnw spring-boot:run
    ;;
  api-gateway|realtime-gateway|media-service)
    echo "Chạy ${choice} (Go)..."
    cd "services/${choice}"
    go run ./cmd
    ;;
  call-service|notification-service)
    echo "Chạy ${choice} (.NET)..."
    cd "services/${choice}"
    dotnet run
    ;;
  *)
    echo "Không nhận diện được service: '${choice}'" >&2
    echo "Các service hợp lệ: ${SERVICES[*]}" >&2
    exit 1
    ;;
esac
