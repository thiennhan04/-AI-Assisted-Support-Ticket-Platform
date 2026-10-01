# Runbook quan sát và vận hành

## 1. Chỉ số cấp service

### Core API

- Availability: request hợp lệ thành công / tổng request hợp lệ.
- Latency: p50/p95/p99 theo route template, không theo raw URI.
- Correctness: tách riêng tỷ lệ 5xx và domain conflict.

### AI pipeline

- Tỷ lệ job thành công theo job type/model/prompt version.
- Độ trễ end-to-end từ queue đến terminal state.
- Tỷ lệ schema validation/repair.
- Tỷ lệ citation bị từ chối/không có tri thức.
- Token/chi phí trên mỗi job thành công.

### Knowledge

- Thành công/thời gian/số trang/số chunk của ingestion.
- Search p95 và tỷ lệ không có kết quả.
- Đánh giá Recall@5 offline theo corpus version.

## 2. Alert đề xuất

| Alert | Điều kiện | Mức độ |
|---|---|---|
| Core API error | 5xx > 2% trong 5 phút | cao |
| Core latency | p95 > mục tiêu trong 10 phút | trung bình |
| DLQ không rỗng | count > 0 trong 5 phút | cao |
| AI provider circuit open | > 2 phút | trung bình |
| AI queue lag | message cũ nhất > 5 phút | trung bình |
| DB pool saturation | active/max > 90% trong 5 phút | cao |
| Knowledge ingest failure | > 5 trong 15 phút | trung bình |
| Tenant gần hết ngân sách | > 80% | thông tin |

## 3. Dashboard

Tạo ba Grafana dashboard:

1. Tổng quan platform: request rate/error/latency, instance health, DB/broker.
2. Chất lượng/chi phí AI: job, failure, token/cost, prompt version, repair và feedback.
3. Knowledge: ingestion queue/status, chunk count, retrieval latency/no-results.

## 4. Runbook: AI job bị chậm

1. Kiểm tra Worker instance và tuổi của message cũ nhất trong queue.
2. Kiểm tra provider circuit và tỷ lệ 429/5xx.
3. Xác minh tenant budget và cấu hình concurrency.
4. Chỉ scale Worker nếu provider và DB còn capacity.
5. Nếu message nằm trong DLQ, kiểm tra error code/correlation ID an toàn.
6. Sửa nguyên nhân gốc rồi replay, giữ nguyên event/job ID.
7. Xác minh duplicate protection và terminal state của job.

## 5. Runbook: câu trả lời sai/rò rỉ

1. Tắt `FEATURE_AI_DRAFT` nếu đang có rủi ro.
2. Thu thập job ID, prompt version, model, citation ID và access-decision log.
3. Xác nhận bộ lọc Knowledge ACL cho principal/tenant bị ảnh hưởng.
4. Tái hiện trong staging bảo mật bằng fixture đã làm sạch.
5. Nếu lỗi prompt, tạo immutable prompt version mới và evaluation case.
6. Nếu lỗi retrieval, sửa index/ACL và chạy lại evaluation.
7. Không sửa job/output lịch sử.

## 6. Runbook: document ingestion bị kẹt

1. Kiểm tra status của document version và lease expiry.
2. Xác nhận object tồn tại, checksum/kích thước khớp.
3. Kiểm tra lỗi extractor và embedding batch.
4. Chỉ dọn staging chunk của phiên bản thất bại.
5. Retry cùng version/job ID khi an toàn.
6. Phiên bản active trước phải luôn tìm kiếm được trong suốt quá trình.
