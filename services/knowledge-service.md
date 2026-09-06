# Knowledge Service - Detail Design

## 1. Responsibilities

- Knowledge document metadata and object upload flow.
- Text extraction, normalization, chunking and embeddings.
- Versioned activation and deletion.
- Tenant/ACL-filtered hybrid search.
- Citation metadata.

## 2. Package structure

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

## 3. Document lifecycle

`UPLOADING -> QUEUED -> PROCESSING -> ACTIVE`, with `FAILED`, `DELETING`, `DELETED` terminal/side states. A previous ACTIVE version remains searchable until the replacement becomes fully ACTIVE.

## 4. Public admin endpoints

- `POST /v1/knowledge/documents/uploads` creates document/upload URL.
- `POST /v1/knowledge/documents/{id}/complete` verifies object and queues ingestion.
- `GET /v1/knowledge/documents` lists tenant documents.
- `GET /v1/knowledge/documents/{id}` shows versions/status/error code.
- `DELETE /v1/knowledge/documents/{id}` removes from search synchronously and purges asynchronously.
- `POST /v1/knowledge/documents/{id}/retry` queues a failed version.

All mutation endpoints require `ADMIN`.

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

Response includes `chunkId`, `documentId`, title, version, page number, excerpt and score. Maximum excerpt 1,500 characters.

## 6. Ingestion algorithm

1. Acquire lease by document version; duplicate events exit.
2. Stream object and verify SHA-256, MIME and size.
3. Extract text page-aware where possible.
4. Normalize Unicode and whitespace; preserve headings and page markers.
5. Reject encrypted/empty documents with stable error code.
6. Chunk by heading/paragraph with target 700 tokens, overlap 100, max 1,000.
7. Compute deterministic chunk checksum from normalized text + metadata.
8. Batch embeddings, default 64 chunks/request or provider limit.
9. Insert chunks/embeddings into staging version.
10. Run validation: non-empty, expected dimensions, no duplicate ordinal.
11. In one transaction, activate new version and deactivate previous.
12. Publish activation event and delete obsolete vectors asynchronously after grace period.

## 7. Search algorithm

1. Validate tenant and principal claims.
2. Normalize query and generate query embedding.
3. Filter candidates by tenant, active version, visibility and ACL before ranking.
4. Run vector similarity top 40 and PostgreSQL full-text top 40.
5. Fuse ranks using Reciprocal Rank Fusion with `k=60`.
6. Remove near-duplicate chunks from same document region.
7. Return topK, maximum 20.

Initial release may use vector-only search behind the same interface. Hybrid search is the target implementation.

## 8. Authorization model

Visibility values:

- `TENANT`: any authenticated tenant user.
- `AGENTS_ONLY`: Agent/Admin only.
- `RESTRICTED`: explicit role or user ACL.

ACL predicates must be part of SQL search, not post-filtered after vector retrieval, to prevent side-channel leakage and weak recall.

## 9. Embedding versioning

Store `embedding_model`, `embedding_dimension` and `chunking_version` per document version. Changing any requires re-index into a new version. Never mix dimensions in one vector column/index.

## 10. Tests

- Extract/chunk fixtures for PDF, DOCX and TXT.
- Deterministic chunk checksums.
- Replacement remains old-active until complete.
- Tenant and restricted ACL search isolation.
- Duplicate ingest idempotency.
- Embedding batch partial failure cleanup.
- Search relevance fixture with Recall@5 baseline.

