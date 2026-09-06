# AI Orchestrator Service - Detail Design

## 1. Responsibilities

- Create and execute AI jobs.
- Resolve prompt template/version and model configuration.
- Retrieve knowledge context where required.
- Call an LLM through provider-neutral ports.
- Validate structured output and citation integrity.
- Record tokens, latency, cost estimate and feedback.
- Publish completed/failed events.

It does not own ticket workflow or document ingestion.

## 2. Package structure

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

## 3. Internal endpoints

### `POST /internal/v1/ai-jobs`

Caller: AI Worker with service credential.

Request includes `jobId`, `tenantId`, `jobType`, `subjectType`, `subjectId`, `input`, `requestedBy`, `correlationId`. `jobId` is client-generated to support retry idempotency.

Response: `200` for already terminal identical job, `202` when accepted/executing, `409` when same job ID has different input hash.

### `GET /internal/v1/ai-jobs/{id}`

Used for reconciliation only. Do not poll during normal event flow.

## 4. Job types

### `ANALYZE_TICKET`

Input: subject, description and allowed metadata. Output schema:

```json
{
  "summary": "string <= 1000",
  "category": "ACCOUNT|PAYMENT|TECHNICAL|GENERAL|OTHER",
  "priority": "LOW|MEDIUM|HIGH|URGENT",
  "rationale": "string <= 1000"
}
```

Do not accept model-generated confidence as calibrated probability. If included, label it `modelSelfScore` and do not use for automated decisions.

### `DRAFT_REPLY`

Input: ticket snapshot, latest comments, tone and locale. Retrieval query is built from subject + normalized description + latest customer comment.

Output schema:

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

`needsHumanReview` is forced to true by backend regardless of model output.

## 5. Prompt management

Prompt templates are immutable after activation. Fields: name, semantic version, job type, system template, user template, output schema, status, checksum.

Promotion flow: `DRAFT -> VALIDATED -> ACTIVE -> RETIRED`. Only one active version per tenant/job type; a global default applies when no tenant override exists.

Every job stores prompt ID/version/checksum and model configuration. Never update historical jobs when a prompt changes.

## 6. Processing algorithm

1. Validate internal caller and request schema.
2. Compute canonical SHA-256 input hash.
3. Insert or load job idempotently.
4. Redact configured PII before provider transmission.
5. For grounded job, query Knowledge Service with tenant/user ACL context.
6. Build prompt with hard separators and untrusted-context label.
7. Call model with low temperature and structured output schema.
8. Parse JSON using strict object mapper; reject unknown fields if schema requires.
9. Apply semantic validation: enum, lengths, forbidden content and citations.
10. If parse fails, attempt one repair call containing validation errors but not secrets.
11. Persist output and usage in one transaction with outbox result event.
12. Return accepted/terminal status.

## 7. Citation validation

- The output citation set must be a subset of retrieved chunk IDs.
- Each citation document ID must match the chunk's owning document.
- Draft sentences containing factual instructions should have at least one citation when knowledge was available.
- If no usable chunks are returned, set `NO_KNOWLEDGE`; do not fabricate citations.
- Do not expose the full stored chunk in Ticket Service; provide short excerpt and document label.

## 8. Provider abstraction

`LanguageModelPort.generate(GenerationRequest): GenerationResult` contains model-neutral messages, JSON schema, timeout and metadata. The domain/application layers must not import provider SDK classes.

Implementations:

- `FakeLanguageModelAdapter` for deterministic development/tests.
- `SpringAiLanguageModelAdapter` for real provider.
- Optional local-model adapter.

## 9. Cost and limits

- Reject input above configured character/token estimate.
- Cap retrieved context and output tokens per job type.
- Record provider/model, input/output tokens, cached tokens, latency and estimated cost.
- Enforce tenant daily budget in Redis with DB reconciliation. Budget exhaustion returns terminal `REJECTED/BUDGET_EXCEEDED` without provider call.

## 10. Tests

- Golden structured outputs for every job type.
- Invalid JSON repair then success/failure.
- Hallucinated citation rejected.
- Provider timeout/retry/circuit behavior.
- Prompt version pinned to job.
- PII redaction.
- Tenant budget concurrency.
- Fake provider end-to-end event contract.

