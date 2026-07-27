#!/usr/bin/env bash
# Idempotent: tạo lại các database/user Postgres còn THIẾU, kể cả khi volume container đã có
# data từ trước (lúc đó infra/postgres/init/*.sh KHÔNG tự chạy lại nữa — đây là script bù cho
# trường hợp đó, VD thêm 1 service mới cần DB mới mà không muốn xoá volume cũ).
#
# Chạy lại bao nhiêu lần cũng an toàn — chỉ CREATE ROLE/DATABASE nếu chưa tồn tại.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

POSTGRES_CONTAINER="chatapp-postgres"

create_db_and_user() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  echo "Bootstrap database '${db_name}' (user '${db_user}')..."
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 --username "${POSTGRES_SUPERUSER}" <<-EOSQL
    DO \$\$
    BEGIN
      IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${db_user}') THEN
        CREATE ROLE ${db_user} WITH LOGIN PASSWORD '${db_password}';
      END IF;
    END
    \$\$;

    SELECT 'CREATE DATABASE ${db_name} OWNER ${db_user}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${db_name}')\gexec

    GRANT ALL PRIVILEGES ON DATABASE ${db_name} TO ${db_user};
EOSQL
}

create_db_and_user "${CORE_DB_NAME}" "${CORE_DB_USER}" "${CORE_DB_PASSWORD}"
create_db_and_user "${NOTIFICATION_DB_NAME}" "${NOTIFICATION_DB_USER}" "${NOTIFICATION_DB_PASSWORD}"
create_db_and_user "${CALL_DB_NAME}" "${CALL_DB_USER}" "${CALL_DB_PASSWORD}"
create_db_and_user "${SOCIAL_DB_NAME}" "${SOCIAL_DB_USER}" "${SOCIAL_DB_PASSWORD}"

echo "Bootstrap xong — tất cả database/user đã tồn tại (tạo mới nếu thiếu, giữ nguyên nếu đã có)."
