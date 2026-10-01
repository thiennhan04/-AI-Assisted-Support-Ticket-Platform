# Quy ước code và khung triển khai

## 1. Phân tầng

- Controller ánh xạ transport DTO sang application command và không chứa business logic.
- Application service xác định transaction boundary và điều phối domain/port.
- Domain object bảo vệ invariant và không phụ thuộc Spring/JPA khi hợp lý.
- Infrastructure triển khai port cho persistence, broker, storage và model provider.
- Có thể tách JPA entity khỏi domain model để giữ ranh giới nghiêm ngặt; trong phạm vi portfolio, một aggregate entity được kiểm soát tốt vẫn chấp nhận được.

## 2. Cách đặt tên

- Command: dùng động từ, ví dụ `CreateTicketCommand`.
- Result/query: `TicketDetail`, `TicketPageQuery`.
- Port: tên capability, ví dụ `LanguageModelPort`, `KnowledgeSearchPort`.
- Adapter: `<Technology><Capability>Adapter`, ví dụ `JpaTicketRepositoryAdapter`, `JpaTicketHistoryRepositoryAdapter`, `SpringAiLanguageModelAdapter`.
- Framework repository giữ framework trong tên, ví dụ `TicketSpringDataRepository`; chỉ infrastructure adapter được inject chúng.
- Event: thì quá khứ và có version, ví dụ `TicketCreatedV1`.
- Error code: uppercase snake case ổn định.

## 3. Mẫu transaction

```java
@Transactional
public TicketId handle(CreateTicketCommand command, Principal principal) {
    policy.checkCreate(principal, command);
    var ticket = Ticket.create(ids.next(), numberGenerator.next(principal.tenantId()), command);
    repository.save(ticket);
    outbox.append(TicketCreatedV1.from(ticket, correlation.currentId()));
    return ticket.id();
}
```

Không publish trực tiếp tới RabbitMQ bên trong transaction này.

## 4. Repository an toàn theo tenant

```java
public interface TicketRepository {
    Optional<Ticket> findById(TenantId tenantId, TicketId ticketId);
    Page<TicketSummary> search(TenantId tenantId, TicketFilter filter, PageRequest page);
    void save(Ticket ticket);
}
```

Code review phải từ chối thao tác tìm tenant entity không giới hạn theo tenant.

## 5. Problem response

Ánh xạ domain exception thành `application/problem+json` gồm `type`, `title`, HTTP `status`, `code` ổn định, `detail` an toàn, `instance`, `correlationId` và tùy chọn field violation. Không lộ stack trace, SQL hoặc provider response.

## 6. Quy tắc REST client

- Cấu hình rõ connect/read timeout.
- Truyền W3C trace context và correlation ID.
- Xác thực bằng service identity.
- Chỉ retry operation đã được xác định là idempotent/retryable.
- Chuyển remote failure thành typed exception tại adapter boundary.
- Dùng circuit breaker cho dependency model/knowledge.

## 7. Mẫu event handler

```java
@Transactional
public void handle(AiAnalysisCompletedV1 event) {
    if (!processedEventRepository.tryInsert(CONSUMER, event.eventId())) return;
    var ticket = repository.findById(event.tenantId(), event.ticketId())
        .orElseThrow(() -> new PermanentEventException("TICKET_NOT_FOUND"));
    ticket.project(event);
    audit.append(AuditEntry.aiAnalysisReceived(ticket, event));
}
```

## 8. DTO và validation

- Dùng Java record cho API/event DTO bất biến.
- Dùng Bean Validation cho transport shape; domain kiểm tra lại business rule.
- Cấu hình Jackson cho ISO date, enum string và giới hạn an toàn.
- Structured AI output dùng DTO/schema riêng, không dùng `Map<String,Object>` trong application code.

## 9. Logging

Dùng parameterized structured log. Gồm `event`, correlation ID, tenant ID, resource/job ID và error code ổn định. Tránh nội dung người dùng. Mỗi exception chỉ được log một lần tại handling boundary.

## 10. Chất lượng build

- Java formatter và import rule trong CI.
- SpotBugs/Error Prone hoặc tương đương.
- ArchUnit rule cho layer và dependency provider bị cấm.
- Quét lỗ hổng dependency và secret.
- Không dùng container tag `latest` hoặc production dependency chưa cố định phiên bản.
