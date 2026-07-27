# Skill: Authentication — Access/Refresh Token, JWKS, Key Rotation

> Áp dụng khi: viết code verify `access_token` ở bất kỳ service nào (API Gateway, Realtime
> Gateway, hoặc service nghiệp vụ tự verify lại cho hành động nhạy cảm), viết code phát hành
> token ở Core Service, hoặc thêm 1 service mới cần biết user hiện tại là ai.
> Đọc cùng `docs/.../system/05-cookie-auth-flow.md` (luồng đầy đủ) — file này tập trung vào
> CÁCH VERIFY chữ ký (JWKS, key rotation), không lặp lại toàn bộ luồng login/refresh/logout.

## Tổng quan cơ chế

- `access_token`: JWT ký **RS256**, TTL 15 phút. Payload chỉ chứa `sub` (user_id), `jti`, `iat`,
  `exp` — không nhét claim nghiệp vụ hay đổi (role, display_name...).
- `refresh_token`: chuỗi UUID v4 ngẫu nhiên, TTL rolling 30 ngày, lưu Redis + `user_sessions`.
- Chỉ **Core Service** giữ private key và được ký token. Mọi service khác chỉ có public key,
  chỉ verify được, không bao giờ tự ký được token.

## JWKS endpoint — cách phân phối public key

- Core Service expose `GET /.well-known/jwks.json` — **endpoint public, không cần auth**, trả
  về danh sách public key đang active theo chuẩn JWK (RFC 7517):

```json
{
  "keys": [
    { "kty": "RSA", "kid": "2026-07-key-1", "n": "...", "e": "AQAB", "use": "sig", "alg": "RS256" }
  ]
}
```

- **Ngoại lệ có chủ đích** so với quy tắc "REST chỉ tồn tại ở lớp API Gateway ↔ client"
  (`skills/service-communication.md`): JWKS luôn là **HTTP GET JSON**, không phải gRPC — vì đây
  là convention chuẩn ngành (RFC + `.well-known`), mọi thư viện JWT ở Go/Java/.NET đều expect
  fetch JWKS qua HTTP sẵn (Nimbus `RemoteJWKSet`, `lestrrat-go/jwx`, `Microsoft.IdentityModel...
  ConfigurationManager`). Không tự viết lại qua gRPC.
- Mỗi JWT access_token có claim `kid` trong HEADER (không phải payload) để consumer biết dùng
  đúng public key nào trong danh sách JWKS đang có nhiều key.

## Cache JWKS ở phía consumer (API Gateway, Realtime Gateway, service nào tự verify)

- Fetch JWKS 1 lần lúc service khởi động, cache toàn bộ trong memory (không phải Redis — đây là
  public key, không nhạy cảm, và cache in-memory nhanh hơn round-trip Redis).
- Refresh định kỳ nền (VD mỗi 10 phút) — không fetch lại mỗi request.
- Nếu gặp `kid` lạ không có trong cache (dấu hiệu vừa rotate key) → fetch lại JWKS ngay 1 lần
  ngoài chu kỳ, **nhưng phải rate-limit việc fetch theo kid lạ** (VD tối đa 1 lần/phút) — nếu
  không, gửi hàng loạt JWT với `kid` giả sẽ làm consumer spam gọi JWKS endpoint (DoS ngược lại
  Core Service).

## Quy trình xoay vòng key (key rotation) — Core Service

Không bao giờ đổi key đột ngột — token cũ ký bằng key cũ vẫn phải verify được tới khi tự hết hạn
(tối đa 15 phút sau khi phát hành):

1. Generate key mới, **thêm vào JWKS** (chưa dùng để ký token nào).
2. Đợi đủ lâu để mọi consumer chắc chắn đã refresh cache và thấy key mới — tối thiểu 2x chu kỳ
   refresh cache (VD chu kỳ 10 phút → đợi ít nhất 20 phút).
3. Chuyển Core Service sang **ký token mới bằng key mới** (`kid` mới trong header).
4. Giữ key cũ trong JWKS thêm **tối thiểu bằng TTL access_token (15 phút)** kể từ lúc ngừng ký
   bằng nó — để các token cũ đã phát hành trước đó vẫn verify được tới khi tự hết hạn.
5. Sau đó mới xoá key cũ khỏi JWKS.

**Không bao giờ** xoá 1 key khỏi JWKS ngay khi vừa ngừng dùng để ký — làm vậy sẽ khiến mọi
access_token vừa phát hành bằng key đó lập tức bị reject dù chưa hết hạn `exp`.

## Quy tắc verify — áp dụng cho mọi service tự verify JWT

1. Đọc `kid` từ JWT header (chưa tin payload lúc này).
2. Tra public key tương ứng trong JWKS cache theo `kid`.
3. Verify signature bằng public key đó (dùng thư viện chuẩn, xem mục Library bên dưới).
4. Check `exp` chưa hết hạn.
5. Nếu service này thuộc nhóm phải chống revoke tức thời (API Gateway, Realtime Gateway — bắt
   buộc; service nghiệp vụ tự verify thêm cho hành động nhạy cảm — tuỳ chọn): Redis GET
   `cache:jwt_revoked_before:{sub}` và `cache:jwt_blacklist:{jti}` (xem
   `docs/.../05-cookie-auth-flow.md` E.6). Bỏ qua bước này ở service không cần chống revoke tức
   thời là chấp nhận được (rủi ro tối đa là user bị revoke vẫn dùng được thêm tới 15 phút ở nơi
   đó — chấp nhận trade-off này phải là quyết định rõ ràng, không phải quên làm).

## Library theo ngôn ngữ — dùng thư viện chuẩn, không tự parse JWT bằng tay

| Ngôn ngữ | Service | Thư viện |
|---|---|---|
| Go | API Gateway, Realtime Gateway, Media Service | `lestrrat-go/jwx/v2` (có sẵn `jwk.Cache` tự fetch/refresh JWKS) |
| Java | Core Service (ký), Messaging, Social Service | `com.nimbusds:nimbus-jose-jwt` (`RemoteJWKSet` tự cache/refresh) |
| .NET | Notification, Call Service | `Microsoft.IdentityModel.Protocols.OpenIdConnect` + `ConfigurationManager<JsonWebKeySet>` |

## Việc KHÔNG được làm

- Không tự viết code parse/verify JWT thủ công (tự split chuỗi theo dấu `.`, tự gọi hàm crypto
  verify tay) — dễ sót lỗi kinh điển (VD không chặn `alg: none`, không chặn algorithm confusion
  RS256↔HS256). Luôn dùng thư viện chuẩn ở bảng trên.
- Không để endpoint JWKS yêu cầu auth — nó PHẢI public, vì mục đích của nó là để mọi service (kể
  cả service chưa từng đăng nhập ai) verify được chữ ký; thêm auth vào đây tạo vòng lặp
  "cần xác thực để lấy thứ dùng để xác thực".
- Không đưa private key vào response JWKS dưới bất kỳ hình thức nào — JWKS chỉ chứa public key.
- Không cache JWKS vĩnh viễn không có cơ chế refresh — sẽ không nhận ra key mới sau khi rotate,
  và không hết hạn key cũ đúng lúc gây rủi ro bảo mật nếu key cũ từng bị lộ.
