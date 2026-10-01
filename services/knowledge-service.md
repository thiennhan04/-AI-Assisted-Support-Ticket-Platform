# Knowledge Service - Thiết kế chi tiết

## 1. Trách nhiệm

- Quản lý metadata tài liệu tri thức và luồng upload object.
- Trích xuất, chuẩn hóa, chia chunk văn bản và tạo embedding.
- Kích hoạt và xóa theo phiên bản.
- Hybrid search có lọc theo tenant/ACL.
- Cung cấp metadata cho citation.

## 2. Cấu trúc package

```text
com.portfolio.knowledge
  api
    DocumentController
    KnowledgeSearchInternalController
  application
    DocumentService
    IngestionService
    SearchService
  domain
    KnowledgeDocument
    DocumentVersion
    Chunk
    EmbeddingPort
    TextExtractorPort
  infrastructure
    persistence
    vector
    storage
    extractor
    messaging
```

### Caller đã xác thực

Knowledge Service tự kiểm tra access token người dùng dùng RS256 bằng JWKS của Identity. Token hợp lệ
phải có issuer/audience đã cấu hình, các claim UUID `sub`, `tid` và danh sách `roles` đã biết, không rỗng.
Danh tính đã xác minh được biểu diễn bằng `com.portfolio.knowledge.application.AuthenticatedPrincipal`;
việc lọc tenant phải dùng `tenantId` từ principal này. Token người dùng bị từ chối tại `/internal/**`
cho đến khi cơ chế xác thực service-to-service được triển khai.

## 3. Vòng đời document

`UPLOADING -> QUEUED -> PROCESSING -> ACTIVE`, cùng các trạng thái kết thúc/phụ `FAILED`, `DELETING`, `DELETED`. Phiên bản ACTIVE trước đó vẫn có thể tìm kiếm cho đến khi phiên bản thay thế chuyển hoàn toàn sang ACTIVE.

## 4. Public admin endpoint

- `POST /v1/knowledge/documents/uploads` tạo document/upload URL.
- `POST /v1/knowledge/documents/{id}/complete` xác minh object và đưa ingestion vào queue.
- `GET /v1/knowledge/documents` liệt kê document của tenant.
- `GET /v1/knowledge/documents/{id}` hiển thị version/status/error code.
- `DELETE /v1/knowledge/documents/{id}` loại khỏi kết quả tìm kiếm đồng bộ và xóa dữ liệu bất đồng bộ.
- `POST /v1/knowledge/documents/{id}/retry` đưa phiên bản thất bại vào queue lại.

Mọi endpoint thay đổi dữ liệu đều yêu cầu role `ADMIN`.

## 5. Internal search endpoint

`POST /internal/v1/knowledge/search`

Request:

```json
{
  "tenantId": "uuid",
  "principalId": "uuid",
  "roles": ["AGENT"],
  "query": "customer cannot reset password",
  "topK": 8,
  "filters": {"tags": ["account"], "locale": "en"}
}
```

Response gồm `chunkId`, `documentId`, title, version, page number, excerpt và score. Excerpt tối đa 1.500 ký tự.

## 6. Thuật toán ingestion

1. Giữ lease theo document version; thoát ngay nếu event bị trùng.
2. Stream object và xác minh SHA-256, MIME, kích thước.
3. Trích xuất văn bản có thông tin trang nếu có thể.
4. Chuẩn hóa Unicode và khoảng trắng; giữ lại heading và dấu trang.
5. Từ chối tài liệu bị mã hóa/rỗng bằng error code ổn định.
6. Chia chunk theo heading/đoạn văn với mục tiêu 700 token, overlap 100, tối đa 1.000.
7. Tính checksum xác định cho chunk từ văn bản và metadata đã chuẩn hóa.
8. Tạo embedding theo batch, mặc định 64 chunk/request hoặc theo giới hạn nhà cung cấp.
9. Thêm chunk/embedding vào phiên bản staging.
10. Kiểm tra: không rỗng, đúng dimension mong đợi, không trùng ordinal.
11. Trong một transaction, kích hoạt phiên bản mới và vô hiệu hóa phiên bản trước.
12. Phát activation event và xóa vector cũ bất đồng bộ sau thời gian gia hạn.

## 7. Thuật toán tìm kiếm

1. Xác minh claim tenant và principal.
2. Chuẩn hóa query và tạo query embedding.
3. Lọc candidate theo tenant, phiên bản active, visibility và ACL trước khi xếp hạng.
4. Chạy vector similarity top 40 và PostgreSQL full-text top 40.
5. Hợp nhất thứ hạng bằng Reciprocal Rank Fusion với `k=60`.
6. Loại các chunk gần trùng trong cùng vùng tài liệu.
7. Trả về topK, tối đa 20.

Phiên bản đầu có thể chỉ dùng vector search phía sau cùng interface. Hybrid search là implementation mục tiêu.

## 8. Mô hình phân quyền

Các giá trị visibility:

- `TENANT`: mọi người dùng đã xác thực thuộc tenant.
- `AGENTS_ONLY`: chỉ Agent/Admin.
- `RESTRICTED`: role hoặc người dùng được cấp ACL rõ ràng.

Điều kiện ACL phải nằm trong truy vấn SQL, không lọc sau khi lấy kết quả vector, nhằm ngăn rò rỉ qua side channel và tránh làm giảm recall.

## 9. Quản lý phiên bản embedding

Lưu `embedding_model`, `embedding_dimension` và `chunking_version` theo từng document version. Thay đổi bất kỳ giá trị nào cũng yêu cầu re-index thành phiên bản mới. Không trộn nhiều dimension trong cùng vector column/index.

## 10. Kiểm thử

- Fixture trích xuất/chia chunk cho PDF, DOCX và TXT.
- Checksum chunk có tính xác định.
- Phiên bản cũ vẫn active cho đến khi bản thay thế hoàn tất.
- Cô lập tìm kiếm theo tenant và restricted ACL.
- Ingestion event trùng vẫn idempotent.
- Dọn dẹp khi một phần embedding batch thất bại.
- Fixture độ liên quan tìm kiếm với baseline Recall@5.
