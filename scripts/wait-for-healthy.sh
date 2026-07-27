#!/usr/bin/env bash
# Đợi 1 hoặc nhiều container Docker đạt trạng thái "healthy" (dựa vào healthcheck khai báo
# trong docker-compose.yml), timeout sau 60s/container. Dùng trong scripts/dev-up.sh, hoặc
# thủ công trước khi chạy migration/kết nối DB, tránh app khởi động sớm hơn hạ tầng.
set -euo pipefail

TIMEOUT_SECONDS=60

for container in "$@"; do
  echo "Đợi ${container} healthy (timeout ${TIMEOUT_SECONDS}s)..."
  elapsed=0
  while true; do
    status="$(docker inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null || echo "starting")"
    if [[ "${status}" == "healthy" ]]; then
      echo "  -> ${container} healthy."
      break
    fi
    if (( elapsed >= TIMEOUT_SECONDS )); then
      echo "  -> TIMEOUT: ${container} vẫn chưa healthy sau ${TIMEOUT_SECONDS}s (status hiện tại: ${status})" >&2
      exit 1
    fi
    sleep 2
    elapsed=$(( elapsed + 2 ))
  done
done
