# **SERVICE 1 – API GATEWAY**

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Go (Fiber framework) |
| **Role** | Entry point duy nhất cho mọi HTTP REST request từ client. Không xử lý WebSocket. |
| **Database** | Không có DB riêng – stateless |
| **Cache** | Redis (check revoke JWT, rate-limit counter) |
| **Giao tiếp** | Verify access_token (JWT) tại chỗ bằng public key — không gRPC mỗi request. gRPC đến Core Service chỉ khi `/auth/refresh` (RefreshAccessToken). Forward HTTP đến downstream services. |
| **Auth** | HttpOnly Secure Cookie: access_token (JWT, TTL 15p) + refresh_token (UUID v4, TTL 30d). Không dùng Authorization header. Chi tiết: `system/05-cookie-auth-flow.md`. |

## **1.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Routing & Reverse Proxy** | Nhận request HTTP, parse path, forward đến service tương ứng theo routing table. Không thay đổi payload. |
| **2** | **Cookie Authentication** | Đọc HttpOnly cookie access_token (JWT) → verify chữ ký RS256 tại chỗ + check `exp` + Redis check revoke (`cache:jwt_revoked_before`, `cache:jwt_blacklist`) → lấy user_id từ claim `sub`. Reject 401 (TOKEN_EXPIRED / TOKEN_REVOKED) nếu invalid. |
| **3** | **Rate Limiting** | Token bucket per IP và per user_id. Lưu counter Redis với TTL. Trả 429 khi vượt ngưỡng. |
| **4** | **Request ID Injection** | Gán X-Request-ID (UUID v4) vào mọi request để trace end-to-end qua các service. |
| **4b** | **Client IP Injection** | Đọc IP thật từ `X-Forwarded-For`/`CF-Connecting-IP`, ghi đè thành header `X-Client-IP` khi forward downstream — luôn strip giá trị `X-Client-IP`/`X-Forwarded-For` do client tự gửi lên trước khi xử lý, chống giả mạo IP. Downstream (Core Service) chỉ tin `X-Client-IP` do Gateway set. Xem `system/05-cookie-auth-flow.md` E.10. |
| **5** | **CORS** | Cấu hình whitelist origin, method, header. Preflight OPTIONS response. |
| **6** | **SSL Termination** | Terminate TLS tại gateway. Downstream dùng HTTP nội bộ. |
| **7** | **Request Logging** | Log mọi request: method, path, status, latency, user_id, request_id vào structured log (JSON). |
| **8** | **Health Check** | GET /health → trả service status. GET /health/live, /health/ready cho K8s probes. |
| **9** | **Circuit Breaker** | Per-downstream circuit breaker: nếu error rate > 50% trong 10s → open circuit, trả 503. |
| **10** | **Response Compression** | Gzip/Brotli response nếu client Accept-Encoding hỗ trợ. |
| **11** | **Timeout Enforcement** | Hard timeout 30s mỗi upstream call. Trả 504 nếu vượt. |
| **12** | **Service Discovery** | Đọc địa chỉ downstream từ env/config, hỗ trợ Kubernetes DNS. |

## **1.2 Naming Convention – Go**

### **Struct / Interface**

type GatewayConfig struct { ... }          // PascalCase

type RouteHandler interface { ... }

type CookieAuthMiddleware struct { ... }

### **Function / Method**

func NewGatewayServer(cfg *GatewayConfig) *Server

func (s *Server) HandleRequest(c *fiber.Ctx) error

### **Variable / Field**

sessionID    string    // camelCase private

RequestID    string    // PascalCase exported field

### **Constants**

const MaxRetryCount = 5

const DefaultTimeoutSec = 30

### **Package**

package middleware   // lowercase, single word

## **1.3 Cấu trúc thư mục**

api-gateway/

├── cmd/

│   └── main.go

├── internal/

│   ├── config/         # GatewayConfig, env loader

│   ├── middleware/

│   │   ├── auth.go     # CookieAuthMiddleware

│   │   ├── ratelimit.go

│   │   ├── requestid.go

│   │   └── cors.go

│   ├── proxy/

│   │   ├── router.go   # Route table

│   │   └── forwarder.go

│   ├── grpc/

│   │   └── identity_client.go

│   └── health/

│       └── handler.go

├── pkg/

│   ├── circuitbreaker/

│   └── logger/

├── Dockerfile

└── go.mod
