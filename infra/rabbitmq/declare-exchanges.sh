#!/usr/bin/env bash
# Chạy 1 lần (container "setup", exit sau khi xong) SAU KHI RabbitMQ đã healthy — khai báo sẵn
# 8 exchange theo docs/.../system/02-rabbitmq-exchange-map.md, tránh lỗi "publish vào exchange
# chưa tồn tại" khi service app publish event lần đầu.
set -euo pipefail

echo "Đợi RabbitMQ management API sẵn sàng..."
for i in $(seq 1 30); do
  if rabbitmqadmin --host=rabbitmq --username="${RABBITMQ_DEFAULT_USER}" --password="${RABBITMQ_DEFAULT_PASS}" \
       list exchanges > /dev/null 2>&1; then
    echo "Management API đã sẵn sàng."
    break
  fi
  if [[ "$i" -eq 30 ]]; then
    echo "TIMEOUT: management API không phản hồi sau 30 lần thử." >&2
    exit 1
  fi
  sleep 2
done

declare -A EXCHANGES=(
  ["user.exchange"]="topic"
  ["chat.exchange"]="topic"
  ["group.exchange"]="topic"
  ["presence.exchange"]="fanout"
  ["notification.exchange"]="topic"
  ["call.exchange"]="topic"
  ["social.exchange"]="topic"
  ["media.exchange"]="topic"
)

for name in "${!EXCHANGES[@]}"; do
  type="${EXCHANGES[$name]}"
  echo "Khai báo exchange '${name}' (type=${type})..."
  rabbitmqadmin --host=rabbitmq --username="${RABBITMQ_DEFAULT_USER}" --password="${RABBITMQ_DEFAULT_PASS}" \
    declare exchange name="${name}" type="${type}" durable=true
done

echo "Đã khai báo xong 8 exchange."
