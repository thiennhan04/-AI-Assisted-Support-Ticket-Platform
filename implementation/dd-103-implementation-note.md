# Ghi chú triển khai DD-103

## Phạm vi đã bàn giao

- Cấu hình Ticket, Knowledge và AI Orchestrator thành OAuth2 Resource Server stateless.
- Thêm cấu hình typed cho issuer, audience, JWKS URI và kiểm tra khi khởi động.
- Chỉ cho phép signature RS256; thêm validation issuer, audience, timestamp, UUID identity, tenant và role.
- Ánh xạ claim đã xác minh vào application principal bất biến, có tenant và authority `ROLE_*`.
- Bật method security, giữ health/info public, bảo vệ mọi user route khác và từ chối user token tại `/internal/**`.
- Thêm Resource Server dependency còn thiếu vào AI Orchestrator.

## Kiểm tra

- Test với Nimbus decoder thật phục vụ JWKS hai key và xác minh token được ký bởi cả private key hiện tại lẫn trước đó.
- Test từ chối signing key không biết, sai issuer/audience, tenant sai/thiếu, thiếu subject, role rỗng hoặc role không biết.
- Contract test kiểm tra ánh xạ principal và Spring authority trong cả ba service.
- Toàn monorepo `./mvnw.cmd -B clean verify` thành công ở cả bảy module, gồm format, Enforcer boundary, unit test, Identity integration test và đóng gói executable JAR.
- Tổng test DD-103: Ticket 5, Knowledge 3, AI Orchestrator 3; tất cả thành công.
- Spring Boot Buildpack tạo các image đã cập nhật:
  - `ticket-platform/ticket-service:0.1.0-SNAPSHOT` (`8527a29ae243`).
  - `ticket-platform/ai-orchestrator-service:0.1.0-SNAPSHOT` (`a504f8c77d5b`).
  - `ticket-platform/knowledge-service:0.1.0-SNAPSHOT` (`70539659bb83`).
