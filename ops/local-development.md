# Phát triển local và khởi tạo repository

## 1. Cấu trúc repository đề xuất

Dùng monorepo để phát triển portfolio nhưng vẫn giữ ranh giới service:

```text
ticket-ai-platform/
  services/
    identity-service/
    ticket-service/
    ai-orchestrator-service/
    knowledge-service/
    ai-worker/
  libs/
    event-contracts/
    test-support/
  deploy/
    compose.yaml
    prometheus/
    grafana/
    otel/
  docs/
  scripts/
  .github/workflows/
```

Không tạo thư viện domain model dùng chung. Chỉ event DTO và test utility được chia sẻ. Quy tắc này ngăn coupling vô tình qua database/entity.

## 2. Khởi tạo từng service

Dependency từ Spring Initializr:

- Spring Web.
- Validation.
- Spring Security và OAuth2 Resource Server.
- Spring Data JPA.
- Flyway.
- PostgreSQL driver.
- Actuator.
- Micrometer tracing/OTLP.
- AMQP cho nơi publish/consume.
- Testcontainers PostgreSQL/RabbitMQ.

AI Orchestrator bổ sung tích hợp Spring AI model. Knowledge Service bổ sung pgvector và thư viện trích xuất tài liệu. Cố định dependency version qua BOM ở root hoặc property do Renovate quản lý.

## 3. Container local

Compose cung cấp:

| Container | Port local |
|---|---:|
| PostgreSQL | 5432 |
| RabbitMQ AMQP/UI | 5672/15672 |
| Redis | 6379 |
| MinIO API/UI | 9000/9001 |
| OpenTelemetry Collector | 4317/4318 |
| Prometheus | 9090 |
| Grafana | 3000 |

Dùng một PostgreSQL container với database/user local riêng: `identity_db`, `ticket_db`, `ai_db`, `knowledge_db`.

## 4. Trình tự chạy cho developer

1. Khởi động hạ tầng: `docker compose up -d postgres rabbitmq redis minio otel prometheus grafana`.
2. Chạy Identity, Ticket, Knowledge và AI Orchestrator với profile `local`.
3. Chạy Worker sau cùng.
4. Chạy local bootstrap script để tạo tenant/admin/sample knowledge.
5. Import `contracts/openapi.yaml` vào API client hoặc sinh frontend client.
6. Dùng fake AI/embedding provider cho đến khi luồng hạ tầng chạy ổn.
7. Đặt provider secret ở local, bên ngoài repository để thử model thật.

### Chạy Identity Service ở local (DD-101)

Từ thư mục gốc repository trên Windows PowerShell:

```powershell
docker compose -f deploy/compose.yaml up -d postgres redis
.\scripts\generate-local-rsa-keys.ps1
$env:SPRING_PROFILES_ACTIVE = "local"
$env:LOCAL_SEED_ENABLED = "true"
$env:LOCAL_SEED_PASSWORD = "ChangeMe123!"
.\mvnw.cmd -pl services/identity-service -am spring-boot:run
```

Seed tùy chọn tạo tenant `acme` và ba user: `admin@acme.local`, `agent@acme.local`, `customer@acme.local`. Chúng dùng password từ `LOCAL_SEED_PASSWORD`. Tắt seed ngoài môi trường local và không bao giờ commit private key; `.local/` đã bị Git ignore.

Ví dụ login:

```powershell
$body = @{
  tenantCode = "acme"
  email = "admin@acme.local"
  password = $env:LOCAL_SEED_PASSWORD
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/auth/login `
  -ContentType application/json -Body $body
```

Để rotate rồi thu hồi refresh-token family được trả về:

```powershell
$tokens = Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/auth/login `
  -ContentType application/json -Body $body
$refreshBody = @{ refreshToken = $tokens.refreshToken } | ConvertTo-Json
$tokens = Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/auth/refresh `
  -ContentType application/json -Body $refreshBody
$logoutBody = @{ refreshToken = $tokens.refreshToken } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/auth/logout `
  -ContentType application/json -Body $logoutBody
```

Public key được công bố tại `http://localhost:8081/.well-known/jwks.json`. Private RSA key chỉ được Identity dùng để ký access token và không bao giờ trả ra ngoài.

### Chạy resource service ở local (DD-103)

Chạy Identity trước, sau đó cấu hình Ticket, Knowledge hoặc AI Orchestrator với cùng token contract:

```powershell
$env:JWT_ISSUER = "http://localhost:8081"
$env:JWT_AUDIENCE = "ticket-platform"
$env:JWKS_URI = "http://localhost:8081/.well-known/jwks.json"
.\mvnw.cmd -pl services/ticket-service -am spring-boot:run
```

Gọi protected API bằng `Authorization: Bearer <accessToken>`. Mỗi resource service tải và cache public key từ JWKS; không bao giờ nhận private key của Identity. Health/info endpoint vẫn public. User token không thể gọi `/internal/**`.

### Thử Ticket CRUD ở local (DD-201)

Giữ Identity chạy, khởi động PostgreSQL nếu cần và chạy Ticket Service với cấu hình JWT trên. Lấy `accessToken` từ `/v1/auth/login`, sau đó tạo và truy vấn ticket:

```powershell
$headers = @{
  Authorization = "Bearer $($tokens.accessToken)"
  "Idempotency-Key" = [guid]::NewGuid().ToString()
}
$ticketBody = @{
  subject = "Cannot sign in"
  description = "The customer cannot sign in to the account"
  priority = "HIGH"
} | ConvertTo-Json
$created = Invoke-WebRequest -Method Post -Uri http://localhost:8082/v1/tickets `
  -Headers $headers -ContentType application/json -Body $ticketBody
$ticket = $created.Content | ConvertFrom-Json
$commentHeaders = @{
  Authorization = $headers.Authorization
  "If-Match" = $created.Headers.ETag
}
$commentBody = @{ body = "I also tried resetting my password"; internal = $false } `
  | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8082/v1/tickets/$($ticket.id)/comments" `
  -Headers $commentHeaders -ContentType application/json -Body $commentBody
Invoke-RestMethod -Method Get -Uri http://localhost:8082/v1/tickets -Headers $headers
```

Không đặt `tenantId` hoặc `requesterId` trong request. Ticket Service lấy cả hai từ access token đã xác minh. Giữ `ETag` trong response và gửi nó dưới dạng `If-Match` khi cập nhật ticket. Lặp create request với cùng `Idempotency-Key` và body trả ticket ban đầu; dùng lại key với body khác trả `409`.

## 5. Hành vi fake provider local

Fake provider phải cho kết quả xác định:

- Subject chứa `password` -> category ACCOUNT.
- Subject chứa `payment` -> category PAYMENT.
- Draft reply cite chunk đầu tiên được trả về.
- Marker đặc biệt `[INVALID_JSON]` kích hoạt test output không hợp lệ.
- Marker đặc biệt `[TIMEOUT]` kích hoạt timeout test.

Nhờ đó end-to-end test ổn định mà không cần API trả phí/network.

## 6. Health endpoint

- `/actuator/health/liveness`: chỉ process/JVM.
- `/actuator/health/readiness`: DB/broker cần thiết cho trách nhiệm service.
- `/actuator/prometheus`: chỉ private network.
- `/actuator/info`: build commit/version, không có secret.

## 7. CI pipeline

Với mỗi pull request:

1. Compile và hiển thị warning.
2. Unit test và architecture test.
3. Integration test bằng Testcontainers.
4. OpenAPI lint và kiểm tra backward compatibility.
5. Event schema validation.
6. Static analysis và quét lỗ hổng dependency.
7. Build container image bất biến, tag bằng commit SHA.

Nhánh main chạy thêm end-to-end fake-provider suite và push image.
