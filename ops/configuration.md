# Tham chiếu cấu hình

## 1. Quy tắc

- Dùng biến môi trường cho khác biệt giữa các deployment.
- Secret dùng tham chiếu/injection từ secret manager và không bao giờ commit.
- Fail fast khi khởi động nếu thiếu cấu hình bắt buộc.
- Dùng typed `@ConfigurationProperties` có validation thay vì `@Value` rải rác.
- Tên profile: `local`, `test`, `staging`, `prod`.

## 2. Biến dùng chung

| Biến | Bắt buộc | Ví dụ/ý nghĩa không phải secret |
|---|:---:|---|
| `SPRING_PROFILES_ACTIVE` | có | `local` |
| `SERVER_PORT` | có | port của service |
| `DB_URL` | có | JDBC URL |
| `DB_USERNAME` | có | tài khoản DB riêng của service |
| `DB_PASSWORD` | secret | được inject |
| `RABBITMQ_HOST` | có | broker hostname |
| `RABBITMQ_USERNAME` | có | service account |
| `RABBITMQ_PASSWORD` | secret | được inject |
| `REDIS_URL` | worker/AI | Redis URI |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | có | collector endpoint |
| `JWT_ISSUER` | có | token issuer URL |
| `JWT_AUDIENCE` | có | `ticket-platform` |
| `JWKS_URI` | service ngoài Identity | Identity JWKS URL |
| `INTERNAL_AUTH_*` | internal caller | cấu hình workload credential |

## 3. Biến của AI Orchestrator

| Biến | Mặc định | Mục đích |
|---|---:|---|
| `AI_PROVIDER` | `fake` ở local | chọn adapter |
| `AI_MODEL_ANALYSIS` | không có | model alias của provider |
| `AI_MODEL_DRAFT` | không có | model alias của provider |
| `AI_API_KEY` | secret | provider credential |
| `AI_REQUEST_TIMEOUT` | `30s` | provider timeout |
| `AI_MAX_RETRIES` | `2` | số lần retry provider call |
| `AI_MAX_INPUT_TOKENS` | `12000` | giới hạn cứng |
| `AI_MAX_OUTPUT_TOKENS` | `1500` | giới hạn cứng |
| `AI_DAILY_TENANT_BUDGET_USD` | `5.00` ở local | giới hạn ngân sách |
| `KNOWLEDGE_BASE_URL` | bắt buộc | internal URL của Knowledge Service |
| `AI_STORE_RAW_DAYS` | `30` | thời gian lưu |

## 4. Biến của Knowledge

| Biến | Mặc định | Mục đích |
|---|---:|---|
| `OBJECT_STORE_ENDPOINT` | MinIO local | object API |
| `OBJECT_STORE_BUCKET` | `knowledge` | bucket |
| `OBJECT_STORE_ACCESS_KEY` | secret | credential |
| `OBJECT_STORE_SECRET_KEY` | secret | credential |
| `EMBEDDING_PROVIDER` | `fake` ở local | adapter |
| `EMBEDDING_MODEL` | được cấu hình | model/version |
| `EMBEDDING_DIMENSION` | `1536` | phải khớp schema |
| `CHUNK_TARGET_TOKENS` | `700` | kích thước mục tiêu |
| `CHUNK_OVERLAP_TOKENS` | `100` | overlap |
| `SEARCH_TOP_K_MAX` | `20` | giới hạn API |

## 5. Biến của Worker

- `WORKER_ANALYSIS_CONCURRENCY=10`
- `WORKER_DRAFT_CONCURRENCY=5`
- `WORKER_INGEST_CONCURRENCY=2`
- `WORKER_JOB_TIMEOUT=60s`
- `AI_ORCHESTRATOR_BASE_URL=http://ai-orchestrator-service:8083`
- `KNOWLEDGE_BASE_URL=http://knowledge-service:8084`

## 6. Feature flag

- `FEATURE_AI_ANALYSIS=true`
- `FEATURE_AI_DRAFT=true`
- `FEATURE_HYBRID_SEARCH=false` ở giai đoạn đầu
- `FEATURE_PII_REDACTION=true`

Flag phải quan sát được và được đánh giá phía server. Tắt AI không ảnh hưởng Ticket CRUD và ngăn tạo AI event mới.
