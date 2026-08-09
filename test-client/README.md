# Test client (throwaway)

Not a real frontend — just a single static page to manually exercise the auth/2FA flow built
so far (register, login, TOTP/EMAIL setup, login-2FA challenge/submit, logout/logout-session/
logout-all, change password, forgot password, session listing) through API Gateway.
Delete/replace once a real frontend project exists.

Each step is a collapsible `<details>` block — click the title bar to expand/collapse. The
response log is pinned to the bottom of the page so you don't have to scroll after every click.
The page UI itself (labels, buttons, section text) is in Vietnamese; this README stays in
English like the rest of the repo's dev docs.

## Run

1. Infra (from repo root): `./scripts/dev-up.sh` — starts Postgres/Mongo/Redis/RabbitMQ on the
   dev ports documented in the root `README.md` (**Postgres 5433, Redis 6380** — not the
   defaults, deliberately changed to avoid clashing with services already on a dev machine).

2. Core Service — use `./scripts/run-service.sh core-service` (recommended) rather than a bare
   `./mvnw spring-boot:run`: the script sources `.env.base` + `.env.dev` + `.env.dev.local` (in
   that order) so mail credentials for the EMAIL 2FA / forgot-password flows get picked up
   automatically. `.env.dev.local` is gitignored — put your own `MAIL_USERNAME`/`MAIL_PASSWORD`
   (a Gmail App Password, not your normal password — https://myaccount.google.com/apppasswords)
   there, never in `.env.dev` (that file IS tracked in git).
   ```
   cd D:\ChatApp
   POSTGRES_PORT=5433 REDIS_PORT=6380 CORE_DB_PASSWORD=core_dev_pw ./scripts/run-service.sh core-service
   ```
   If you skip mail setup entirely, everything except EMAIL 2FA and forgot-password still works
   (TOTP 2FA needs no mail creds).

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
so the HttpOnly cookies (access_token/refresh_token/pre_auth_token/reset_token) actually get
stored and sent back by the browser, exactly like a real client would.

## Testing the logout flow

- **Section 5 (logout current device)** and **section 10 (check auth status)** work standalone
  in this single page — login, hit "Check auth status" (expect 200), hit "Logout", hit "Check
  auth status" again (expect 401).
- **Section 6 (sessions)** lists every active session via `GET /auth/sessions` and lets you
  logout any OTHER device — either 1 at a time (per-row button) or all at once (the "Đăng xuất
  tất cả thiết bị KHÁC" button next to "Tải lại danh sách", which calls `POST
  /auth/logout-all?keep_current=true` and refreshes the table). No more manual SQL lookups. To
  actually have a 2nd session to test against, log in from a separate incognito window/browser
  first (this page reuses 1 cookie jar, so logging in again here just rotates the same session).
  The row for the device you're using right now is highlighted and has no logout button (use
  section 5 for that).
- Logging out 1 device from section 6 blacklists that device's access_token `jti` immediately
  (session rows track `access_token_jti` since migration `V20260808150000`) — its "check auth
  status" flips to 401 right away, not after up to 15 minutes. Bulk logout (section 6's "logout
  others" button, or section 9 with "keep current device" checked) still only revokes
  refresh_token for each other session at once — no immediate per-jti blacklist there since
  it's not acting on 1 known session.

## Testing register (section 1)

- Registration is 2-step: `/auth/register/otp` (validates uniqueness, stashes the pending account
  in Redis, sends an OTP) then `/auth/register/verify` (verifies the OTP, only then actually
  creates the row + logs in). No user row exists in between — a wrong/expired code just leaves
  nothing behind, no cleanup needed.
- Email XOR phone is enforced server-side (`RegisterRequest.isExactlyOneOfEmailOrPhoneProvided`)
  — filling both (or leaving both blank) gets rejected with 400 `VALIDATION_ERROR` before any
  business logic runs, regardless of what calls the API (this page, curl, Postman). Try it in
  section 1 to see the 400 firsthand.

## Testing forgot password (section 8)

- Step 1 (`/auth/password/forgot`) always returns 200 whether or not the email is registered —
  that's intentional (no account-enumeration), not a bug. Check the inbox of whatever email you
  used — `OtpMailSender` sends a real email via `JavaMailSender`/SMTP (`MAIL_USERNAME`/
  `MAIL_PASSWORD` in `.env.dev.local`, see "Run" step 2 above) for the actual 6-digit code, same
  as the existing EMAIL 2FA method in section 3.
- Step 2 sets a `reset_token` cookie scoped to `Path=/auth/password` — nothing to copy by hand,
  just click step 3's button right after step 2 succeeds and the browser sends it automatically.
- Step 3 clears the `reset_token` cookie and revokes every existing session for that account
  (same as change password) — log in again with the new password afterward.
- Phone/SMS OTP delivery (register, 2FA later) goes through `OtpSmsSender` — no real provider
  chosen yet, it just logs the code to core-service's own console/log output instead of sending a
  real SMS. Copy the code from there when testing a phone-based flow.

## Testing Google OAuth login (section 2b)

- Needs a Google Cloud Console OAuth client (Credentials -> Create Credentials -> OAuth client
  ID -> Web application) with **Authorized JavaScript origins = `http://localhost:5500`** and
  **Authorized redirect URIs = `http://localhost:5500/`** — this page IS the redirect target
  ("Kiểu B", see `docs/.../06-business-flows.md` F.2: the page itself catches Google's redirect
  and POSTs the code, Core Service never sees Google's redirect directly).
- Put the client_id/client_secret from that screen into `.env.dev.local`:
  ```
  GOOGLE_OAUTH_CLIENT_ID=...
  GOOGLE_OAUTH_CLIENT_SECRET=...
  GOOGLE_OAUTH_REDIRECT_URI=http://localhost:5500/
  ```
  (Core Service reads these via `OAuthProperties` — see `application.yml`'s `app.oauth.providers.google`.)
- Paste the same client_id into the "google-client-id" field in section 2b (only the secret is
  server-side; the client_id is public and only needed here to build the redirect URL).
- After clicking through Google's consent screen, this page reloads at `http://localhost:5500/
  ?code=...&state=...` — a bit of JS (`handleOAuthRedirectIfAny`, runs automatically on load)
  checks the `state` it stashed in `sessionStorage` before redirecting (basic CSRF guard,
  entirely client-side — Core Service never sees `state`), then POSTs the code to
  `/auth/oauth/google/callback`. If the account already has 2FA enabled, this behaves exactly
  like section 2 (password login) — use section 4 to complete the challenge.
- 1 Google account = 1 ChatApp user, no account linking (see `03-core-service.md`): a Google
  email that already belongs to an existing user (password or a different provider) gets
  rejected with `OAUTH_EMAIL_ALREADY_REGISTERED`, not silently merged.
