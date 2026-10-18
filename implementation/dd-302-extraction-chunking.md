# DD-302 – Trích xuất và chia chunk

## Phạm vi

Đọc PDF/DOCX/TXT, chuẩn hóa, chia chunk xác định và lưu chunk bằng JPA.
Không thêm public API, embedding/search hay scheduler. Upload DD-301 vẫn trả QUEUED;
publisher/consumer DD-501 sẽ gọi bean IngestionService.prepare(tenantId, documentId, versionId).
ID phải lấy từ event hệ thống đã xác minh, không lấy tenantId từ input công khai.
Hiện có thể kiểm thử entry point này qua DocumentUploadIT, chưa tự chạy sau upload.

## Luồng xử lý

1. Tìm document theo tenant, kiểm tra version hiện hành. Đã có prepared_at thì thoát.
2. Chỉ xử lý QUEUED; tối đa hai lần chuẩn bị đồng thời trong mỗi JVM.
3. Tải MinIO (tối đa 25 MiB), xác minh size và SHA-256 so với metadata đã lưu.
4. Parser đọc theo MIME đã xác minh khi upload; từ chối dữ liệu không đọc được.
5. Chuẩn hóa Unicode NFC, khoảng trắng/xuống dòng; giữ trang và heading khi có thông tin.
6. Chia chunk; tính checksum từ nội dung và metadata chuẩn hóa.
7. Trong transaction ngắn: khóa document, kiểm tra lại version/trạng thái, lưu toàn bộ chunk,
   chunking_version, prepared_at và trạng thái PROCESSING.

Propagation.NOT_SUPPORTED ở entry point giữ I/O/parse ngoài transaction của caller.
Row lock chỉ ở adapter khi ghi kết quả. Đây là chống commit trùng, chưa phải distributed lease:
hai instance có thể đọc/parse trùng nhưng chỉ một bộ chunk được lưu. Lease, ACK/DLQ và retry/backoff
thuộc DD-501. Không đổi active_version_id, không xóa chunk cũ, không phát activation event.
PROCESSING nghĩa là còn chờ embedding; prepared_at phân biệt đã chuẩn bị chunk hay chưa.

## Trích xuất và giới hạn

- PDF: PDFBox 3.0.7, số trang thật từ 1, giữ đoạn văn; không đoán heading từ font/layout.
- DOCX: Apache POI 5.5.1, heading chuẩn Heading1–Heading9, đoạn văn và bảng ở body.
  page_number NULL vì phân trang phụ thuộc renderer Word.
- TXT: UTF-8 nghiêm ngặt, chấp nhận BOM; heading Markdown # đến ######; page_number NULL.
- Chưa có OCR, layout analysis, header/footer hoặc mọi style Word tùy chỉnh.

PDF ảnh không có chữ được xem là DOCUMENT_EMPTY. Office mã hóa OLE được nhận diện trong extractor;
upload DD-301 vẫn từ chối OLE ngay từ MIME vì không phải DOCX ZIP hợp lệ.
Giới hạn bổ sung: PDF 500 trang; text thô 2.000.000 ký tự; DOCX 2.000 ZIP entry,
8 MiB/entry và 32 MiB sau giải nén; 5.000 chunk. Giới hạn không thay antivirus/sandbox.
Chưa có hard timeout/process isolation; cần bổ sung sandbox/giới hạn tài nguyên nếu nhận file từ Internet.

## Chunk xác định

JTokkit 1.1.0 chạy local, không gọi AI hay cần API key. Encoding CL100K_BASE:
mục tiêu 700 token, trần 1.000, overlap tối đa 100. Ưu tiên đoạn văn gần mục tiêu;
không nối qua section khác trang/heading. Đoạn dài chia theo token, không cắt lỗi Unicode.
Overlap có thể ít hơn 100 để giữ ký tự UTF-8; chunk cuối có thể ngắn hơn mục tiêu.

Checksum SHA-256 gồm chunking_version, ordinal, trang, heading và nội dung chuẩn hóa;
các field có phân cách/độ dài rõ ràng. Chunk ID xác định từ tenant, version, ordinal và checksum.
Ordinal từ 0. Cùng input/cùng chunking_version cho cùng kết quả. Thay thuật toán/tokenizer cần
đổi chunking_version và tạo version tài liệu mới, không sửa chunk đã chuẩn bị.

## Lưu dữ liệu và xử lý lỗi

V004 chỉ thêm bảng/cột, không sửa V001–V003. document_chunk có composite FK tenant/document
và document/version, UNIQUE(version, ordinal), token_count 1–1000; chưa có vector column.
Lỗi ghi chunk rollback toàn bộ chunk, prepared_at và trạng thái.

| Mã lỗi | Xử lý |
|---|---|
| DOCUMENT_EMPTY / DOCUMENT_ENCRYPTED / DOCUMENT_INVALID | FAILED, không lưu chunk |
| DOCUMENT_TOO_LARGE / UNSUPPORTED_DOCUMENT_TYPE | FAILED, không lưu chunk |
| DOCUMENT_CONTENT_MISMATCH | FAILED khi size/SHA-256 không khớp |
| STORAGE_UNAVAILABLE / INGESTION_BUSY | Giữ QUEUED để retry |
| Lỗi database | Rollback, không đánh dấu file hỏng |

Lưu mã lỗi vào version; không log nội dung tài liệu. FAILED chưa tự retry.
Không có endpoint xem chunk công khai để tránh bỏ qua kiểm tra ACL của bước search.

## Thứ tự đọc source

1. IngestionServiceTest rồi IngestionService: flow nghiệp vụ.
2. TextExtractor và DocumentTextExtractorAdapter: đọc thành Section.
3. DocumentChunkerTest rồi DocumentChunker: chia text/checksum.
4. JpaDocumentRepositoryAdapter.saveChunks/failPreparation: transaction/chống trùng.
5. ChunkJpaEntity và V004: cấu trúc lưu DB.
6. DocumentUploadIT: PostgreSQL/MinIO, rollback và xử lý đồng thời.

## Kiểm thử

```powershell
.\mvnw.cmd -B -pl services/knowledge-service -am spotless:check clean test
.\mvnw.cmd -B -pl services/knowledge-service -am clean verify
```

Lệnh test không cần Docker; verify chạy thêm integration test và cần Docker.
Fixture PDF/DOCX được tạo ngay trong test, không dựa vào file cá nhân hay tài nguyên mạng.
TXT có BOM/Unicode/heading. Test chunk kiểm tra overlap, không mất chữ, checksum ổn định.
Ngày 2026-10-10: 42 unit test Knowledge pass; clean test toàn monorepo pass 67 test.
Root verify -DskipITs cũng pass: compiler, Enforcer, formatter, unit test và đóng gói toàn bộ module.
Đây không phải full verify vì integration test đã được bỏ qua rõ ràng.
Đã chạy verify Knowledge: compiler, unit test và formatter pass; integration test chưa xác minh
do Docker Desktop engine không kết nối được. Đã thêm IT lưu chunk, tenant/version, FAILED khi rỗng,
rollback và hai lần chuẩn bị đồng thời. Task chưa đánh dấu hoàn thành khi còn thiếu xác minh.

## Nguồn thư viện

- [PDFBox API](https://pdfbox.apache.org/3.0/migration.html)
- [Apache POI](https://poi.apache.org/)
- [JTokkit](https://github.com/knuddelsgmbh/jtokkit)
