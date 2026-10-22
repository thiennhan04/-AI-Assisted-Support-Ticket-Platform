# DD-303 – Embedding và vector search

## Luồng chính và thứ tự đọc

1. DD-302 gọi IngestionService.prepare để tạo chunk và đánh dấu PROCESSING.
2. Workflow nội bộ gọi IndexingService.index(tenantId, documentId, versionId).
3. IndexingService tải chunk, gọi EmbeddingPort theo batch 64, kiểm tra từng vector 1.536 chiều.
4. Khi tất cả batch thành công, JpaKnowledgeIndexRepositoryAdapter.activate khóa document,
   kiểm tra lại version và lưu vector + metadata model + trạng thái ACTIVE cùng transaction.
5. KnowledgeSearchInternalController nhận service JWT, kiểm tra identity trong body khớp delegation đã ký.
6. SearchService chuẩn hóa query, tạo vector bằng cùng EmbeddingPort, gọi repository tìm kiếm.
7. ChunkSpringDataRepository.search lọc quyền trong SQL trước khi xếp hạng cosine, trả citation metadata.

Đọc IndexingServiceTest và SearchServiceTest trước, sau đó hai service, EmbeddingPort,
FakeEmbeddingAdapter/HttpEmbeddingAdapter, cuối cùng adapter JPA và truy vấn search.
Code dùng biến trung gian cho từng bước, không thêm command/mapper class cho mỗi thao tác.

## Chạy local không cần tài khoản AI

Mặc định EMBEDDING_PROVIDER=fake. FakeEmbeddingAdapter chuyển từ thành vector hash xác định,
chỉ phục vụ dev/test và kiểm tra pipeline. Nó không hiểu từ đồng nghĩa hoặc ngữ nghĩa như model thật.
Không cần API key, không gọi Internet, model ID là fake-word-hash-v1.

Sau upload, document vẫn QUEUED cho đến khi workflow được gọi. Chưa có scheduler tự lấy outbox.
DD-501 sẽ nối event với hai lời gọi trên. Không có endpoint công khai để kích hoạt index hoặc thay ACL.
Có thể chạy DocumentUploadIT để kiểm thử upload → prepare → index → search trước khi nối RabbitMQ.

## Adapter HTTP thật

```text
EMBEDDING_PROVIDER=http
EMBEDDING_URL=https://api.openai.com/v1/embeddings
EMBEDDING_API_KEY=<secret-from-environment>
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_SPACE_ID=provider-account-v1
```

Endpoint phải HTTPS. Adapter gửi model/input/dimensions=1536/encoding_format=float,
kiểm tra response model, index từng phần tử, số lượng, dimension, vector hữu hạn và khác zero.
Kết quả provider có thể đảo thứ tự; adapter xếp lại bằng index trước khi gắn với chunk.
Connect timeout 5 giây, request timeout 30 giây. Lỗi provider trả mã an toàn 503,
không log text/vector/key hoặc giữ response body nhạy cảm trong exception.
Chưa tự retry HTTP để tránh phát sinh chi phí lặp ngoài kiểm soát; workflow có thể retry lại phiên bản.
Không tự fallback từ HTTP sang fake. Chưa chạy provider thật trong kiểm thử dự án.

HTTP mode gửi nội dung chunk/câu hỏi sang provider và có thể phát sinh phí của tài khoản cấu hình.
Kiểm tra chính sách dữ liệu của tổ chức trước khi dùng tài liệu thật. Budget/cost record/circuit breaker
thuộc epic model invocation, chưa hiện thực trong task này.

Model ID lưu DB là http/<space-id>/<model>. Đổi provider/endpoint/thuật toán phải đổi space ID hoặc model,
re-index dưới version mới; không âm thầm trộn vector giữa model/namespace. Database cố định 1.536 chiều;
đổi dimension cần migration và chiến lược index mới.

## Kích hoạt nguyên tử và retry

Vector của mọi batch được giữ tạm trong RAM, chỉ ghi khi toàn bộ kết quả hợp lệ.
Batch thứ hai lỗi không lưu kết quả batch thứ nhất; giữ version PROCESSING và prepared_at để retry.
Hai job index đồng thời có thể gọi provider trùng, nhưng row lock và kiểm tra active làm commit idempotent.
Tối đa hai job index chạy đồng thời trong mỗi JVM; lease phân tán thuộc DD-501.
Không giữ transaction DB trong lúc chờ provider. Retry version đã active với cùng model không gọi provider.
Đổi model trên version active trả EMBEDDING_MODEL_CONFLICT, yêu cầu version mới.
Phiên bản cũ không bị xóa vector; truy vấn chỉ đọc active_version_id. Luồng thay file/xóa và
activation event/cleanup version cũ hoàn thiện ở DD-304.

## API nội bộ và xác thực

```text
POST http://localhost:8084/internal/v1/knowledge/search
Authorization: Bearer <SERVICE_JWT>
Content-Type: application/json
```

```json
{
  "tenantId": "00000000-0000-4000-8000-000000000001",
  "principalId": "00000000-0000-4000-8000-000000000103",
  "roles": ["CUSTOMER"],
  "query": "How do I reset my password?",
  "topK": 8,
  "filters": {"tags": ["account"]}
}
```

Service JWT phải RS256, issuer/JWKS đã cấu hình, aud=knowledge-internal,
sub=ai-orchestrator-service, scope có knowledge:search, exp/iat bắt buộc.
Claims tid, principal_id và roles chứa delegation người dùng đã được issuer xác minh/ký;
ba field identity trong body phải khớp chính xác. Tenant/principal/roles thực dùng lấy từ token.
User accessToken từ login hiện tại không dùng cho endpoint này. Identity hiện chưa cấp service delegation
token; cần issuer/workload credential ở bước tích hợp service. Không thêm endpoint mint token thử nghiệm.
Test dùng RSA key và JWKS server riêng trong test để xác minh chữ ký, expiry, audience và scope thật.

Cấu hình SERVICE_JWT_ISSUER và SERVICE_JWKS_URI có thể trỏ đến workload issuer;
giá trị mặc định dùng issuer local Identity, audience/subject cố định theo contract.
Không dùng chung user JwtDecoder cho internal chain. Contract:
[knowledge-internal.openapi.yaml](../contracts/knowledge-internal.openapi.yaml).

## Truy vấn và ACL

- Tenant bắt buộc, chunk phải thuộc active_version_id, version ACTIVE và đúng embedding model/dimension.
- Document DELETING/DELETED bị loại; chunk staging hoặc chưa có vector không được tìm thấy.
- ALL_TENANT_USERS: người dùng cùng tenant.
- SUPPORT_TEAM_ONLY: AGENT/ADMIN cùng tenant.
- LIMITED_ACCESS: chỉ USER hoặc ROLE có row document_acl; ADMIN không mặc nhiên vượt ACL.
- Tất cả tags request phải thuộc tags tài liệu. topK mặc định 8, tối đa 20; query tối đa 2.000 ký tự.
- Locale chưa có metadata lưu trữ, không nhận filter locale; field lạ bị từ chối.

CTE MATERIALIZED lọc tenant/active/model/ACL trước khi cosine ranking. Native SQL nằm trong Spring Data
@Query, không dùng JdbcTemplate ở production. JPA/Hibernate vector module ánh xạ float[] qua SqlTypes.VECTOR.
Ban đầu dùng exact cosine search để kết quả/quyền dễ kiểm tra. Chưa dùng HNSW/ANN, full-text hoặc RRF;
đó là bước tối ưu sau khi có dữ liệu đo latency/recall. Query trả tối đa 1.500 ký tự excerpt cùng
chunkId/documentId/title/version/pageNumber/heading/score; không trả vector/objectKey.

## Migration

V005 thêm vector(1536) nullable vào chunk staging; document_version thêm embedding_model,
embedding_dimension và activated_at; thêm document_acl. Không sửa V001–V004.
Extension vector đã được deploy/postgres tạo sẵn. Migration có CREATE EXTENSION IF NOT EXISTS;
nếu database mới chưa có extension thì cần DBA/provisioning tạo trước cho service user không có quyền.

## Kiểm thử

```powershell
.\mvnw.cmd -B -pl services/knowledge-service -am clean verify
```

Unit test: batch order/failure, invalid vectors, retry idempotency, query validation,
HTTP response ordering/malformed payload và service JWT claims.
Security test: RSA/JWKS thật, user token/wrong audience/expired/missing scope, tenant spoofing.
DocumentUploadIT: PostgreSQL pgvector và MinIO thật, pipeline index/search, tenant/active/model/ACL,
rollback khi vector sai chiều, duplicate delivery và Recall@5.
Fixture search/recall-fixture.json có tám tài liệu/tám query, một document liên quan cho mỗi query.
Baseline fake yêu cầu Recall@5 = 1.0; đây chỉ là kiểm tra hồi quy pipeline với từ khóa,
không chứng minh chất lượng ngữ nghĩa model thật. Model thật cần bộ fixture paraphrase và đánh giá riêng.

Kết quả 2026-10-18: clean verify Knowledge/shared libs thành công, 56 unit/security test,
19 integration test, không skip. Recall@5 fixture fake đạt 1.0 (8/8 query tìm đúng document trong top 5).
Đã kiểm tra V001→V005, rollback vector/active pointer khi database từ chối activation và retry thành công.
Integration test phát hiện field version bị đọc khi lazy proxy chưa khởi tạo ở DD-302;
DocumentSpringDataRepository nay dùng EntityGraph cho find và join fetch trong truy vấn có khóa.

## Nguồn

- [Hibernate vector mapping](https://github.com/pgvector/pgvector-java#hibernate)
- [pgvector distance functions](https://github.com/pgvector/pgvector)
- [OpenAI embedding HTTP contract](https://developers.openai.com/api/reference/resources/embeddings/methods/create)

Adapter HTTP được triển khai dựa trên OpenAI Docs; runtime fake/test không cần key hoặc tài khoản OpenAI.
