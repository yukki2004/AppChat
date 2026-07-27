#!/usr/bin/env bash
# Chạy TỰ ĐỘNG bởi image postgres chính thức, CHỈ 1 LẦN lúc volume data còn rỗng
# (docker-entrypoint-initdb.d). Tạo 4 database + 4 user least-privilege, mỗi user chỉ có
# quyền trên đúng 1 database của mình — đúng tinh thần database-per-service ở mức credential.
#
# Nếu volume đã có data từ trước (container restart, không phải lần đầu), script này KHÔNG
# chạy lại — dùng scripts/db-bootstrap.sh ở host để tạo bổ sung DB còn thiếu trong trường hợp đó.
set -euo pipefail

create_db_and_user() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
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
