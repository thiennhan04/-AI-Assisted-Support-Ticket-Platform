# Hạ tầng local DD-002

Thư mục này chứa hạ tầng local cần cho năm application module. Nó không chạy các application image.

## Thành phần được provision

- PostgreSQL 16 có pgvector và một persistent volume.
- Bốn database/user riêng cho service: `identity_db`, `ticket_db`, `ai_db`, `knowledge_db`.
- Extension `vector` trong `knowledge_db`.
- RabbitMQ có management UI, Redis dùng AOF persistence và MinIO có private bucket `knowledge`.
- OpenTelemetry Collector, Prometheus và Grafana data source/dashboard đã provision.

Mọi image đều được cố định phiên bản. Mọi thành phần có state dùng named Docker volume. Credential local lấy từ `.env` ở thư mục gốc và có giá trị mặc định chỉ dành cho phát triển trong `.env.example`.

## Khởi động

Từ thư mục gốc repository:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f deploy/compose.yaml up -d
docker compose --env-file .env -f deploy/compose.yaml ps
```

Container chạy một lần `minio-init` phải kết thúc với exit code 0. Container chạy dài hạn phải báo `healthy`.

## Endpoint local

| Thành phần | Endpoint |
|---|---|
| PostgreSQL | `localhost:5432` |
| RabbitMQ | `amqp://localhost:5672` |
| RabbitMQ UI | `http://localhost:15672` |
| Redis | `localhost:6379` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| OTLP gRPC/HTTP | `localhost:4317` / `localhost:4318` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |

Dùng credential từ `.env`. Không dùng credential mẫu bên ngoài máy local.

## Dừng hoặc reset

Dừng container nhưng giữ dữ liệu:

```powershell
docker compose --env-file .env -f deploy/compose.yaml down
```

Lần khởi động sau dùng lại named volume. Khi chủ động muốn xóa toàn bộ dữ liệu local DD-002, thêm `--volumes` vào lệnh. Thao tác này phá hủy dữ liệu và không thuộc quy trình dọn dẹp thông thường.
