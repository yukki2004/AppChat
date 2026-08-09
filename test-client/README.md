# Test client (throwaway)

Not a real frontend — just a single static page to manually exercise the auth/2FA flow built
so far (register, login, TOTP/EMAIL setup, login-2FA challenge/submit, logout/logout-session/
logout-all, change password, forgot password, session listing, QR login) through API Gateway
(and, for QR login only, a direct WebSocket to Realtime Gateway).
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

3b. Realtime Gateway — only needed for section 11 (QR login); skip it if you're not testing that.
   Easiest via `./scripts/run-service.sh realtime-gateway` (sources `.env.dev`, which already has
   `APP_REDIS_ADDR`/`APP_RABBITMQ_URL` set) — or manually:
   ```
   cd services/realtime-gateway
   APP_REDIS_ADDR=localhost:6380 APP_RABBITMQ_URL=amqp://chatapp_dev:rabbitmq_dev_pw@localhost:5673/ go run ./cmd
   ```
   Listens on `:8081` (`config/base.yaml`) — the test page's WS connects straight to it, not
   through API Gateway (API Gateway doesn't handle WS).

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

## Testing register + link identifier (section 1, 2c)

- Registration is 2-step: `/auth/register/otp` (validates uniqueness, stashes the pending account
  in Redis, sends an OTP) then `/auth/register/verify` (verifies the OTP, only then actually
  creates the row + logs in). No user row exists in between — a wrong/expired code just leaves
  nothing behind, no cleanup needed.
- Email XOR phone is enforced server-side (`RegisterRequest.isExactlyOneOfEmailOrPhoneProvided`,
  same for `LinkIdentifierRequest`) — filling both (or leaving both blank) gets rejected with 400
  `VALIDATION_ERROR` before any business logic runs, regardless of what calls the API (this page,
  curl, Postman). Try it in section 1 to see the 400 firsthand.
- Section 2c adds the identifier you *didn't* register with (re-auths via password, target column
  must currently be NULL) — after it succeeds, log in again in section 2 using the newly-linked
  identifier; no extra wiring needed since login already looks up by username/email/phone.
- Every OTP send (register, link, 2FA, forgot-password — all go through the same
  `OtpCodeService.generate()`) is rate-limited to 1 per 60s per (target, purpose)
  (`OTP_RESEND_TOO_SOON`, `OTP_RESEND_COOLDOWN_SECONDS` env var) — stops the endpoint being used
  to spam someone else's real inbox/phone. If you click "gửi OTP" twice quickly while testing,
  the 2nd click is expected to 429, not a bug.

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

## Testing from a phone on the same LAN (for QR login's 2-device flow)

Section 11 as described below simulates both devices in 1 browser tab — good enough to verify
the flow works, but not a real "quét QR bằng máy khác" test. To use an actual phone as the
"thiết bị cũ" (already logged-in device confirming), instead of a 2nd tab on the same PC:

1. Find your PC's LAN IP (Windows: `ipconfig`, look for the Wi-Fi/Ethernet adapter's IPv4 —
   e.g. `192.168.1.20`). Phone and PC must be on the same Wi-Fi/network.
2. Start API Gateway with that IP allowed for CORS:
   ```
   APP_CORS_EXTRA_ORIGINS=http://192.168.1.20:5500 ./scripts/run-service.sh api-gateway
   ```
   (`run-service.sh` doesn't source this one from `.env.dev` — it's per-machine/per-network, set
   it inline each time instead of committing your LAN IP into a tracked file.)
3. Serve this page as usual (`python -m http.server 5500`) — `http.server` already binds all
   interfaces, no change needed there.
4. On the PC, open `http://192.168.1.20:5500` (**not** `localhost` — must match the origin you
   allowed in step 2, since the page's JS derives the API/WS URLs from whatever host loaded it).
5. On the phone's browser, open the exact same `http://192.168.1.20:5500`.
6. Log in on the PHONE (section 2) — the phone is now "thiết bị cũ".
7. On the PC: section 11 part A, "Tạo QR + mở WS chờ".
8. On the phone: section 11 part B. The page's own camera scanner ("Quét QR bằng camera",
   `BarcodeDetector` API) **will NOT work over plain `http://<lan-ip>:5500`** — browsers only
   allow camera access on a secure context (`https://` or `localhost`), and a LAN IP over HTTP
   is neither. Paste the `qr_token` shown under the QR on the PC into the phone's
   `qrlogin-token` field by hand instead, then device-info + confirm.

   Want the camera scan to actually work from the phone? Put an HTTPS tunnel in front of port
   5500 (e.g. `ngrok http 5500`) and open the `https://...ngrok...` URL on the phone instead of
   the LAN IP — that's a secure context, so the camera prompt will appear. (You'd also need
   `GATEWAY`/`REALTIME_GATEWAY_WS` in `index.html` to resolve to a reachable API/WS host in that
   case too, not just the page itself — out of scope for a quick local test.)
9. Watch the PC's WS status line flip to approved and auto-claim.

## Testing QR login (section 11)

Simulates both devices on this one page/1 cookie jar — realistic enough since the "new device"
init/claim calls don't need a cookie at all, and the "old device" confirm call just needs
whatever session is currently logged in via section 2:

1. Log in normally first (section 2) — this page is now "the old, already-logged-in device".
2. Section 11, part A: "Tạo QR + mở WS chờ" — calls `POST /auth/qr-login/init` (public), shows
   the QR code + raw `qr_token`, opens a WebSocket straight to `realtime-gateway` at
   `ws://localhost:8081/ws/qr-login?token=...` and waits.
3. Section 11, part B: "Xem thông tin thiết bị đang xin login" (`GET .../device-info`, uses the
   session from step 1) — shows the IP/User-Agent the QR session was created with (this page's
   own IP/UA, since steps A and B run in the same browser) — this is the security-gate step a
   real device would show the user before confirming. Then "Xác nhận đăng nhập (approve)"
   (`POST .../confirm`).
4. Watch part A's status line — once core-service's outbox publishes `user.qr_login_approved`
   and realtime-gateway pushes it down the WS, the page auto-calls `POST .../claim`. **This
   overwrites the page's current `access_token`/`refresh_token` cookies** with the newly-claimed
   session — expected behavior (the "new device" really did just log in), not a bug. Re-run
   section 10 ("Kiểm tra trạng thái") afterward if you want to confirm the session is now the
   claimed one.
5. To see the 90s expiry path instead: do step 2 only, then wait without doing step 3 — the
   status line switches to "QR đã hết hạn" once realtime-gateway's TTL timer fires.

If part A's status stays stuck on "Đang mở WS..." or shows a WS error, `realtime-gateway` isn't
running — see "Run" step 3b above.

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
