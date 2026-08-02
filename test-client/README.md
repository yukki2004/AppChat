# Test client (throwaway)

Not a real frontend — just a single static page to manually exercise the auth/2FA flow built
so far (register, login, TOTP/EMAIL setup, login-2FA challenge/submit, logout/logout-session/
logout-all) through API Gateway. Delete/replace once a real frontend project exists.

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

## Testing the logout flow

- **Section 5 (logout current device)** and **section 8 (check auth status)** work standalone
  in this single page — login, hit "Check auth status" (expect 200), hit "Logout", hit "Check
  auth status" again (expect 401).
- **Section 6 (logout 1 other device)** and testing "logout-others" in **section 7** need a
  *second* active session to revoke, which this page can't create by itself — it only holds 1
  cookie jar. Log in from a separate incognito window (or a different browser) to create a 2nd
  session, then find its `id` via `SELECT id, device_name, ip_address, created_at FROM
  user_sessions WHERE user_id = '<uuid>' AND is_active = true;` and paste it into section 6.
  There's no "list sessions" endpoint yet to fetch this from the API itself.
- Section 6 and "logout-others" in section 7 revoke the target's refresh_token immediately, but
  can't blacklist its access_token (Core Service has no per-session `jti`) — expect that other
  device's "Check auth status" to keep returning 200 for up to 15 more minutes. This is the
  documented tradeoff, not a bug.
