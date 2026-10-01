# Thiết kế bảo mật và threat model

## 1. Trust boundary

1. Browser/client tới public gateway: mạng và input không đáng tin cậy.
2. Gateway tới service: có user token nhưng mỗi service vẫn tự authorize.
3. Giữa các service: private network và workload credential/mTLS khi có.
4. Nền tảng tới model provider: ranh giới xử lý dữ liệu bên ngoài.
5. Nền tảng tới object storage: byte upload không đáng tin cậy.
6. Nội dung knowledge đưa vào LLM: có thể chứa chỉ dẫn độc hại.

## 2. Xác thực

- Public API yêu cầu RS256 access JWT, trừ login, refresh, logout và JWKS.
- Refresh token là opaque token, chỉ lưu SHA-256 hash, rotate mỗi lần dùng và revoke cả family khi
  phát hiện reuse. Response login/refresh dùng `Cache-Control: no-store`.
- Xác minh signature, issuer, audience, expiry và claim bắt buộc `sub`/`tid`.
- Internal endpoint từ chối user token và yêu cầu service identity đúng audience/scope.
- Signing private key chỉ tồn tại trong secret store của Identity Service.
- Khi rotate key, public key hiện tại và trước đó cùng tồn tại đến khi hết TTL token tối đa.

## 3. Ma trận phân quyền

| Hành động | Customer | Agent | Admin |
|---|:---:|:---:|:---:|
| Tạo ticket | của mình | requester bất kỳ trong tenant | có |
| Xem ticket | của mình | trong tenant | trong tenant |
| Assign/đổi priority | không | có | có |
| Internal comment | không | có | có |
| Yêu cầu/duyệt AI draft | không | có | có |
| Upload knowledge | không | không | có |
| Xem knowledge dành cho agent | không | có | có |
| Quản lý user | không | không | có |

Annotation ở controller chỉ là defense-in-depth. Application service phải gọi policy với principal đã
xác thực cùng tenant/ownership của resource.

## 4. Cô lập tenant

- Repository method bắt buộc nhận `tenantId`; application không được dùng `findById` thiếu scope.
- Lấy tenant từ token đã xác minh, không lấy từ request body/query.
- Login là ngoại lệ duy nhất: nhận `tenantCode` đã chuẩn hóa vì chưa có token. Identity đổi code
  thành tenant UUID và ký vào claim `tid`; endpoint đã xác thực chỉ dùng claim này.
- Resource khác tenant trả 404 nếu có nguy cơ enumeration.
- Integration test phải tạo dữ liệu cùng hình dạng ở hai tenant.
- Production có thể thêm PostgreSQL RLS; predicate ở application vẫn bắt buộc.

## 5. Rủi ro riêng của AI

### Prompt injection từ ticket hoặc document

- Xem mọi nội dung người dùng/tài liệu là dữ liệu đặt trong delimiter rõ ràng.
- System prompt quy định nội dung retrieval không được override policy hay gọi tool trái phép.
- Tool do server định nghĩa; model không tự tạo URL hoặc SQL.
- Yêu cầu của model không làm tăng quyền truy cập document.
- Tool argument phải qua typed schema và server validation.

### Rò rỉ dữ liệu

- Lọc tenant/ACL trước khi kết quả retrieval rời Knowledge Service.
- Che giá trị nhạy cảm đã cấu hình trước khi gọi provider.
- Mặc định không log prompt hoặc completion.
- Cấu hình retention của provider phải phù hợp policy dự án.
- Không đưa API key, internal prompt hoặc hidden metadata vào nội dung retrieval.

### Hallucination và tự động hóa không an toàn

- AI không tự thay đổi category/priority đã chấp nhận trong v1.
- AI draft cần người duyệt.
- Citation ID phải thuộc tập chunk đã retrieval.
- Không có knowledge là kết quả hợp lệ; không bịa hướng dẫn để fallback.

### Denial of wallet/service

- Ngân sách token/chi phí hằng ngày theo tenant.
- Giới hạn input/output/context token.
- Rate limit và worker concurrency hữu hạn.
- Từ chối document quá lớn và archive đệ quy.
- Circuit breaker và queue backpressure.

## 6. Kiểm soát file upload

- Chỉ cho PDF, DOCX và plain text theo nội dung phát hiện được, không chỉ dựa tên file.
- Tối đa 25 MB, 500 trang.
- Object key opaque; tên gốc chỉ lưu làm metadata sau khi sanitize.
- Xác minh SHA-256 và malware scan trước khi activate/download.
- V1 từ chối file có mật khẩu hoặc mã hóa.
- Signed URL hết hạn sau 15 phút và chỉ dùng cho đúng object key/content length.

## 7. Bảo vệ API

- Validate mọi request DTO và từ chối field nhạy cảm không được khai báo.
- CORS allowlist theo environment.
- Bearer token trong Authorization header không cần CSRF; nếu refresh token dùng secure cookie thì
  phải thêm CSRF protection.
- Rate-limit riêng login, tạo upload, tạo ticket và AI draft.
- Trả problem detail an toàn; log nguyên nhân nội bộ cùng correlation ID.

## 8. Chính sách log

Không bao giờ log password/token, toàn bộ ticket description/comment ở production, raw
prompt/completion, signed URL, document chunk hoặc embedding vector. Có thể log có kiểm soát tenant
ID, ticket ID, job ID, model alias, token count, status/error code.

## 9. Acceptance test bảo mật

- Từ chối JWT algorithm confusion và sai audience.
- Hành vi đúng với session hết hạn/revoked.
- Kiểm tra mọi trường hợp cross-tenant.
- Không suy ra restricted knowledge qua score/count/error.
- Prompt injection fixture không thể kích hoạt tool hay lộ system instruction.
- Từ chối citation bịa, filename độc hại và MIME mismatch.
- Rate limit trả 429 kèm `Retry-After`.
