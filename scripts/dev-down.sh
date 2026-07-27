#!/usr/bin/env bash
# Dừng hạ tầng dev. Thêm flag -v để xoá luôn volume (mất sạch data) — mặc định KHÔNG xoá.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "-v" ]]; then
  docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v
else
  docker compose -f docker-compose.yml -f docker-compose.dev.yml down
fi
