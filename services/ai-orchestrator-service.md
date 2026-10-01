# AI Orchestrator Service - Thiết kế chi tiết

## 1. Trách nhiệm

- Tạo và thực thi các AI job.
- Xác định template/phiên bản prompt và cấu hình model.
- Truy xuất ngữ cảnh tri thức khi cần.
- Gọi LLM thông qua các port độc lập với nhà cung cấp.
- Kiểm tra structured output và tính toàn vẹn của citation.
- Ghi nhận token, độ trễ, chi phí ước tính và phản hồi.
- Phát các event hoàn thành/thất bại.

Service này không sở hữu quy trình ticket hoặc quy trình nhập tài liệu.

## 2. Cấu trúc package

```text
com.portfolio.ai
  api
    AiJobInternalController
    PromptAdminController
  application
    AiJobService
    AnalysisPipeline
    DraftReplyPipeline
    OutputValidationService
  domain
    AiJob
    PromptTemplate
    ModelUsage
    ports/LanguageModelPort
    ports/KnowledgeSearchPort
  infrastructure
    provider/SpringAiLanguageModelAdapter
    knowledge/KnowledgeRestClient
    persistence
    messaging
```

### Caller đã xác thực

AI Orchestrator tự kiểm tra access token người dùng dùng RS256 bằng JWKS của Identity, rồi ánh xạ các claim
`sub`, `tid` và `roles` đã được xác minh vào `com.portfolio.ai.application.AuthenticatedPrincipal`. API
quản trị prompt có thể dùng các role authority từ principal này. Access token người dùng bị từ chối tại
`/internal/**`; lời gọi từ AI Worker chưa khả dụng cho đến khi cơ chế định danh service riêng được triển khai.

## 3. Internal endpoint

### `POST /internal/v1/ai-jobs`

Bên gọi endpoint: AI Worker dùng service credential.

Request gồm `jobId`, `tenantId`, `jobType`, `subjectType`, `subjectId`, `input`, `requestedBy`, `correlationId`. `jobId` do client tạo để hỗ trợ retry idempotent.

Response: `200` nếu job giống hệt đã ở trạng thái kết thúc, `202` khi đã nhận/đang chạy, `409` khi cùng job ID nhưng khác input hash.

### `GET /internal/v1/ai-jobs/{id}`

Chỉ dùng để đối soát. Không polling trong luồng event thông thường.

## 4. Loại job

### `ANALYZE_TICKET`

Dữ liệu vào: subject, description và metadata được cho phép. Schema đầu ra:

```json
{
  "summary": "string <= 1000",
  "category": "ACCOUNT|PAYMENT|TECHNICAL|GENERAL|OTHER",
  "priority": "LOW|MEDIUM|HIGH|URGENT",
  "rationale": "string <= 1000"
}
```

Không coi confidence do model sinh ra là xác suất đã được hiệu chỉnh. Nếu có trường này, đặt tên là `modelSelfScore` và không dùng để ra quyết định tự động.

### `DRAFT_REPLY`

Dữ liệu vào: snapshot của ticket, các comment mới nhất, tone và locale. Retrieval query được tạo từ subject + description đã chuẩn hóa + comment mới nhất của khách hàng.

Schema đầu ra:

```json
{
  "reply": "string <= 10000",
  "citations": [
    {"chunkId": "uuid", "documentId": "uuid", "label": "string"}
  ],
  "needsHumanReview": true,
  "riskFlags": ["NO_KNOWLEDGE|SENSITIVE_DATA|UNCERTAIN"]
}
```

Backend luôn ép `needsHumanReview` thành true, bất kể output của model.

## 5. Quản lý prompt

Prompt template là bất biến sau khi được kích hoạt. Các trường gồm: name, semantic version, job type, system template, user template, output schema, status và checksum.

Luồng phát hành: `DRAFT -> VALIDATED -> ACTIVE -> RETIRED`. Mỗi tenant/job type chỉ có một phiên bản active; dùng bản mặc định toàn hệ thống nếu tenant không cấu hình ghi đè.

Mỗi job lưu prompt ID/version/checksum và cấu hình model. Không bao giờ cập nhật job lịch sử khi prompt thay đổi.

## 6. Thuật toán xử lý

1. Xác thực internal caller và request schema.
2. Tính SHA-256 hash chuẩn hóa của input.
3. Thêm mới hoặc tải job theo cách idempotent.
4. Che dữ liệu PII đã cấu hình trước khi gửi tới nhà cung cấp.
5. Với job cần nguồn tri thức, truy vấn Knowledge Service kèm ngữ cảnh ACL của tenant/người dùng.
6. Dựng prompt với dấu phân cách rõ ràng và nhãn đánh dấu ngữ cảnh không đáng tin cậy.
7. Gọi model với temperature thấp và structured output schema.
8. Parse JSON bằng object mapper nghiêm ngặt; từ chối field lạ nếu schema yêu cầu.
9. Kiểm tra ngữ nghĩa: enum, độ dài, nội dung bị cấm và citation.
10. Nếu parse thất bại, thử một repair call chứa lỗi validation nhưng không chứa secret.
11. Trong cùng một transaction, lưu output, usage và outbox result event.
12. Trả trạng thái accepted/terminal.

## 7. Kiểm tra citation

- Tập citation trong output phải là tập con của các chunk ID đã truy xuất.
- Document ID của mỗi citation phải khớp với document sở hữu chunk đó.
- Khi có tri thức phù hợp, các câu hướng dẫn mang tính sự thật trong bản nháp nên có ít nhất một citation.
- Nếu không có chunk dùng được, đặt `NO_KNOWLEDGE`; không tự tạo citation.
- Không trả toàn bộ nội dung chunk đã lưu cho Ticket Service; chỉ cung cấp đoạn trích ngắn và nhãn document.

## 8. Lớp trừu tượng nhà cung cấp

`LanguageModelPort.generate(GenerationRequest): GenerationResult` chứa messages, JSON schema, timeout và metadata độc lập với model. Tầng domain/application không được import class từ SDK của nhà cung cấp.

Các implementation:

- `FakeLanguageModelAdapter` cho môi trường phát triển/test có kết quả xác định.
- `SpringAiLanguageModelAdapter` cho nhà cung cấp thật.
- Có thể bổ sung adapter cho local model.

## 9. Chi phí và giới hạn

- Từ chối input vượt quá ước tính ký tự/token đã cấu hình.
- Giới hạn context được truy xuất và output token theo từng job type.
- Ghi nhận provider/model, input/output token, cached token, độ trễ và chi phí ước tính.
- Áp dụng ngân sách hằng ngày theo tenant trong Redis và đối soát với DB. Khi hết ngân sách, trả trạng thái kết thúc `REJECTED/BUDGET_EXCEEDED` mà không gọi nhà cung cấp.

## 10. Kiểm thử

- Golden structured output cho từng job type.
- JSON không hợp lệ được repair rồi thành công/thất bại.
- Từ chối citation không có trong nguồn truy xuất.
- Hành vi timeout/retry/circuit của nhà cung cấp.
- Phiên bản prompt được cố định theo job.
- Che PII.
- Xử lý đồng thời ngân sách tenant.
- Luồng event end-to-end với fake provider.
