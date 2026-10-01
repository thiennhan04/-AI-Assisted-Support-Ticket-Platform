# AI Worker - Thiết kế chi tiết

## 1. Trách nhiệm

- Nhận các event của ticket và knowledge.
- Chuyển event thành internal command gọi tới service tương ứng.
- Thực thi chính sách retry mà không tạo trùng business state.
- Phát tín hiệu lỗi vận hành/đối soát.

Worker không lưu domain state và không gọi trực tiếp LLM.

## 2. Consumer

| Queue | Routing key | Handler |
|---|---|---|
| `ai.ticket-analysis.q` | `ticket.created.v1`, `ticket.analysis.requested.v1` | `TicketAnalysisConsumer` |
| `ai.ticket-draft.q` | `ticket.draft.requested.v1` | `TicketDraftConsumer` |
| `knowledge.ingest.q` | `knowledge.ingest.requested.v1` | `KnowledgeIngestConsumer` |

Mỗi queue có retry exchange và DLQ.

## 3. Mẫu xử lý

1. Deserialize envelope và kiểm tra schema version.
2. Đưa metadata trace/correlation/tenant vào context.
3. Giữ Redis lease `worker:{consumer}:{eventId}` với TTL 90 giây.
4. Gọi internal endpoint của service sở hữu dữ liệu, dùng `eventId`/`jobId` làm định danh idempotency.
5. Chỉ acknowledge khi thành công ở trạng thái kết thúc hoặc xác nhận là bản gọi trùng idempotent.
6. Với lỗi có thể retry, reject sang retry route và tăng attempt header.
7. Với lỗi validation/authorization vĩnh viễn, phát failure event rồi acknowledge.
8. Xóa context và giải phóng lease.

Redis lease chỉ là tối ưu hóa, không phải ranh giới bảo đảm tính đúng đắn. Tính đúng đắn đến từ unique constraint phía service.

## 4. Lịch retry

Các lần thử sau lần gửi đầu: 10 giây, 1 phút, 5 phút, 30 phút. Sau đó chuyển vào DLQ. Giữ nguyên `eventId`; bổ sung các header `attempt`, `firstOccurredAt`, `lastErrorCode`.

Lỗi có thể retry:

- HTTP 408, 429, 502, 503, 504.
- Lỗi kết nối/timeout.
- Lỗi database hoặc broker tạm thời.

Lỗi vĩnh viễn:

- Event schema không hợp lệ.
- Không tìm thấy subject sau khoảng trễ đối soát.
- Tenant không khớp.
- Job type không được hỗ trợ.
- Bị từ chối theo chính sách của nhà cung cấp.

## 5. Xử lý đồng thời

- Ticket analysis prefetch: 10 mỗi instance.
- Draft reply prefetch: 5 mỗi instance.
- Knowledge ingest prefetch: 2 mỗi instance.
- Các thread pool riêng ngăn việc nhập tài liệu lớn chiếm hết tài nguyên của ticket analysis.
- Graceful shutdown dừng nhận việc mới và dành 60 giây cho công việc đang chạy.

## 6. Đối soát

Scheduled reconciliation job chạy mỗi 15 phút:

- Tìm các AI request trong Ticket Service vẫn ở trạng thái `QUEUED` quá năm phút.
- Kiểm tra AI Orchestrator theo job ID.
- Phát lại request bị thiếu với cùng job/event identity.
- Không bao giờ tạo logical job ID mới.

## 7. Kiểm thử

- Nhận event trùng chỉ tạo một job.
- Phân loại lỗi có thể retry và lỗi vĩnh viễn.
- Retry header và hành vi DLQ.
- Truyền trace.
- Graceful shutdown.
- Cô lập các concurrency pool.
