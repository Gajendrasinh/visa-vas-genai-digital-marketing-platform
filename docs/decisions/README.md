# Architecture Decision Records

Format: each ADR records Problem, Options, Decision, Trade-offs, and the scaling, failure, security, operational and cost implications.

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-microservices.md) | Service decomposition: consolidated microservices | Proposed |
| [ADR-002](ADR-002-kafka-event-backbone.md) | Apache Kafka as the event backbone | Proposed |
| [ADR-003](ADR-003-transactional-outbox.md) | Transactional outbox for DB → Kafka | Proposed |
| [ADR-004](ADR-004-rag.md) | Retrieval-augmented generation design | Proposed |
| [ADR-005](ADR-005-vector-store-opensearch.md) | OpenSearch as search and vector store | Proposed |
| [ADR-006](ADR-006-llm-gateway.md) | Own AI gateway with provider adapters | Proposed |
| [ADR-007](ADR-007-agent-architecture.md) | Controlled graph-based agents with LangGraph (Python) | Accepted |
| [ADR-008](ADR-008-mcp.md) | MCP as the only AI-to-business-data interface | Proposed |
| [ADR-009](ADR-009-kubernetes-eks.md) | Kubernetes on Amazon EKS | Proposed |
| [ADR-010](ADR-010-observability.md) | OpenTelemetry-based observability | Proposed |
| [ADR-011](ADR-011-java25-spring-boot4.md) | Java 25 and Spring Boot 4 | Accepted |
| [ADR-012](ADR-012-maven-monorepo.md) | Maven multi-module monorepo | Proposed |
| [ADR-013](ADR-013-avro-schema-registry.md) | Avro with Schema Registry | Proposed |
| [ADR-014](ADR-014-kafka-streams.md) | Kafka Streams for real-time processing | Proposed |
| [ADR-015](ADR-015-postgres-schema-per-service.md) | PostgreSQL, schema-per-service | Proposed |
| [ADR-016](ADR-016-data-lake.md) | S3 data lake with raw and curated zones | Proposed |
| [ADR-017](ADR-017-keycloak-token-exchange.md) | Keycloak for identity with token exchange | Proposed |
| [ADR-018](ADR-018-testing-strategy.md) | Testing strategy | Proposed |
| [ADR-019](ADR-019-deterministic-ai-boundary.md) | Deterministic business rules vs AI responsibilities | Proposed |
| [ADR-020](ADR-020-ai-evaluation-gate.md) | AI evaluation as a CI quality gate | Proposed |
| [ADR-021](ADR-021-multi-tenancy.md) | Multi-tenancy by financial institution | Proposed |
| [ADR-022](ADR-022-polyglot-java-python.md) | Polyglot boundary: Java core, Python for orchestration and evaluation | Accepted |
| [ADR-023](ADR-023-localstack-local-aws.md) | LocalStack for local AWS APIs | Accepted |
