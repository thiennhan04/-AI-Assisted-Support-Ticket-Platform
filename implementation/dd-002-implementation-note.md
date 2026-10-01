# Ghi chú triển khai DD-002

Đã triển khai và kiểm tra ngày 2026-09-06.

## Nội dung đã thêm

- `deploy/compose.yaml` với phiên bản image cố định cho PostgreSQL/pgvector, RabbitMQ, Redis, MinIO, OpenTelemetry Collector, Prometheus và Grafana.
- Named volume bền vững cho mọi thành phần có state và một internal network dùng chung.
- Khởi tạo PostgreSQL với bốn database/user riêng cho từng service và pgvector trong `knowledge_db`.
- Container khởi tạo MinIO chạy một lần để tạo private bucket `knowledge`.
- Prometheus scrape chính nó và OpenTelemetry Collector.
- Grafana được provision sẵn Prometheus data source và dashboard tổng quan hạ tầng.
- `.env.example` ở thư mục gốc chứa giá trị mặc định chỉ dùng local; file `.env` thật vẫn bị gitignore.
- Kiểm tra cấu hình Compose trong CI verify job hiện có.
- `deploy/README.md` hướng dẫn khởi động, dừng, endpoint, credential và reset.

Năm image Spring Boot cố ý chưa được đưa vào Compose này. DD-002 cung cấp các dependency hạ tầng; cấu hình runtime và business migration của service được bổ sung trong story tương ứng.

## Bằng chứng kiểm tra

- `docker compose -f deploy/compose.yaml config --quiet` thành công.
- PostgreSQL, RabbitMQ, Redis, MinIO, OpenTelemetry Collector, Prometheus và Grafana đều báo `healthy`.
- `minio-init` hoàn thành một lần với exit code 0.
- PostgreSQL có `identity_db`, `ticket_db`, `ai_db`, `knowledge_db` và bốn service role tương ứng.
- Extension pgvector trong `knowledge_db` báo phiên bản `0.8.1`.
- RabbitMQ xác thực thành công tài khoản local đã cấu hình.
- MinIO có private bucket `knowledge`.
- Prometheus báo cả hai scrape target `prometheus` và `otel-collector` là `up`.
- Grafana trả HTTP 200 và có dashboard `Infrastructure Overview` đã provision.
- Sau khi restart PostgreSQL/Redis, toàn bộ database, pgvector và giá trị Redis tạm vẫn còn. Redis key tạm đã được xóa sau khi kiểm tra.
- Maven reactor `clean verify` từ thư mục gốc vẫn thành công sau DD-002.
