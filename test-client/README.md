# Test client (throwaway)

Not a real frontend — just a single static page to manually exercise the auth/2FA flow built
so far (register, login, TOTP/EMAIL setup, login-2FA challenge/submit) through API Gateway.
Delete/replace once a real frontend project exists.

## Run

1. Infra (from repo root): `./scripts/dev-up.sh` — starts Postgres/Mongo/Redis/RabbitMQ on the
   dev ports documented in the root `README.md` (**Postgres 5433, Redis 6380** — not the
   defaults, deliberately changed to avoid clashing with services already on a dev machine).

2. Core Service — needs the dev ports above, since its own defaults assume 5432/6379:
   ```
   cd services/core-service
   POSTGRES_PORT=5433 REDIS_PORT=6380 CORE_DB_PASSWORD=core_dev_pw ./mvnw spring-boot:run
   ```
   (`CORE_DB_PASSWORD` value comes from `.env.dev` at repo root.)

3. API Gateway — same Redis port override, via its `APP_` env prefix:
   ```
   cd services/api-gateway
   APP_REDIS_PORT=6380 go run ./cmd
   ```

4. This page, served on the **fixed port 5500** (Gateway's CORS only allows this origin —
   see `services/api-gateway/cmd/main.go`):
   ```
   cd test-client && python -m http.server 5500
   ```
   Then open http://localhost:5500 — do NOT just double-click index.html (file:// origin
   won't match the CORS allowlist or send cookies correctly).

## What it does

Talks to API Gateway at `http://localhost:8080` with `fetch(..., { credentials: 'include' })`
so the HttpOnly cookies (access_token/refresh_token/pre_auth_token) actually get stored and
sent back by the browser, exactly like a real client would.
