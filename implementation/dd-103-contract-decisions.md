# Quyết định contract DD-103

## Contract tin cậy token

- Ticket, Knowledge và AI Orchestrator là OAuth2 Resource Server cho API hướng người dùng.
- Chỉ chấp nhận access token RS256.
- `JWT_ISSUER`, `JWT_AUDIENCE`, `JWKS_URI` là contract triển khai bắt buộc; giá trị local mặc định trỏ tới Identity ở port 8081.
- Signature, thời gian expiry/not-before, issuer và audience được kiểm tra trước khi ánh xạ claim thành application principal.
- `sub`, `tid` bắt buộc là chuỗi UUID. `roles` là array không rỗng, chỉ chứa `CUSTOMER`, `AGENT` hoặc `ADMIN`.

## Principal và phân quyền

- Mỗi bounded context sở hữu `application.AuthenticatedPrincipal` chứa `userId`, `tenantId` và tập role bất biến.
- JWT role được ánh xạ thành Spring authority có prefix `ROLE_` để phân quyền ở method.
- Application và persistence code chỉ lấy tenant từ principal đã xác minh. Không bao giờ tin request body, query parameter hoặc header tùy ý như danh tính tenant.
- `/actuator/health/**` và `/actuator/info` là public; route hướng người dùng khác yêu cầu access token hợp lệ.

## Ranh giới service-to-service

- Token người dùng bị từ chối tại `/internal/**`.
- Workload credential và internal scope/audience là phạm vi triển khai riêng. Trước khi có cơ chế đó, internal endpoint phải fail closed thay vì nhận user token.

## Xoay key

- Resource service tìm key theo JWT `kid` từ Identity JWKS.
- Identity công bố public key hiện tại và key trước đó trong khoảng overlap, cho phép token đã phát vẫn hợp lệ đến khi hết hạn tự nhiên.
- Private signing key chỉ nằm trong Identity và không bao giờ sao chép sang resource service.
