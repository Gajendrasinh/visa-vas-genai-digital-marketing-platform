# VAS Digital Marketing & GenAI Platform (reference implementation)

A reference platform for issuer-run, card-linked digital marketing: real-time behavioral signals → features → segments → deterministic offer eligibility → personalized, human-approved campaigns → analytics, with a governed GenAI layer (AI gateway, RAG, agents, MCP, guardrails, evaluation).

All data is synthetic. The project is inspired by public descriptions of card-network value-added services and uses no proprietary implementation details.

**Status: Phases 0–4 complete (architecture, build, domain + PostgreSQL, REST + security, Kafka + outbox). Next: Phase 5, streaming + data platform.** See [docs/roadmap.md](docs/roadmap.md).

## Local setup

Prerequisites: Java 25, Docker (give Docker Desktop ≥ 6 GB memory), [uv](https://docs.astral.sh/uv/). Maven comes via `./mvnw`.

```bash
make env        # creates .env from .env.example (placeholder values; edit as needed)
make test       # Java: tests + Spotless + SpotBugs + JaCoCo + enforcer; Python: ruff + mypy + pytest
make up-core    # Postgres, Kafka + topic catalog, Schema Registry, Redis, Keycloak (+ LocalStack if token set)
make down       # stop (volumes kept); `make clean` also deletes volumes
```

| Component | Local endpoint | Notes |
|---|---|---|
| PostgreSQL | `localhost:5432` db `vasmkt` | one schema + role per service (`svc_<schema>`, password `SERVICE_DB_PASSWORD`) |
| Kafka | `localhost:9094` | auto-create disabled; catalog in `infrastructure/docker/kafka/topics.txt` |
| Schema Registry | `http://localhost:8085` | BACKWARD_TRANSITIVE |
| Redis | `localhost:6379` | password `REDIS_PASSWORD` |
| Keycloak | `http://localhost:8180` realm `vasmkt` | demo users `admin, manager, approver, analyst, data, aiops, viewer` / `KEYCLOAK_DEMO_USER_PASSWORD` |
| LocalStack | `http://localhost:4566` | needs `LOCALSTACK_AUTH_TOKEN` (free Hobby plan); S3, KMS, Secrets Manager |

Service ports: gateway 8080, customer 8081, segmentation 8082, offer 8083, campaign 8084. Each serves OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

Local tokens: the realm's `vasmkt-local-cli` client (local only) allows a password grant. Demo users belong to tenant `issuer-alpha`, except `manager-beta` (`issuer-beta`). With `make up-core`, gateway and campaign-service running, `./scripts/smoke-api.sh` exercises token → gateway → service → Postgres, including four-eyes approval and tenant isolation.

LLM provider is chosen with `AI_PROVIDER` in `.env` (`stub` by default; `anthropic`, `openai`, `bedrock`, `ollama`). All values in `.env.example` are placeholders.

## Design documents

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Principles, deterministic vs AI boundary, container view, component responsibilities, technology choices, key sequence flows, environments, SLOs, consistency review, open questions |
| [docs/data-model.md](docs/data-model.md) | Data ownership, ER diagrams, tables, Redis keys, OpenSearch indices, lake layout, data quality, lineage, lifecycle |
| [docs/kafka.md](docs/kafka.md) | Event envelope, topic catalog and partitioning rationale, outbox, retries/DLQ, idempotency, streaming topologies, schema evolution |
| [docs/api.md](docs/api.md) | API conventions (versioning, errors, idempotency, pagination, ETags) and endpoint catalog |
| [docs/ai-architecture.md](docs/ai-architecture.md) | AI gateway, routing, prompts, structured output, egress policy, RAG, agents, tools, MCP, A2A, memory, guardrails, evaluation, latency, cost |
| [docs/security.md](docs/security.md) | Identity, RBAC matrix, encryption, secrets, privacy, threat model, AI threat model |
| [docs/roadmap.md](docs/roadmap.md) | Phased implementation plan with exit criteria |
| [docs/decisions/](docs/decisions/README.md) | ADR-001 … ADR-021 |

## Planned repository structure

```
.
├── pom.xml                      # Maven parent (Java modules)
├── pyproject.toml               # uv workspace (Python services)
├── platform/                    # technical shared libraries — no domain types
│   ├── common-web/              # ProblemDetail, correlation, idempotency, pagination
│   ├── common-kafka/            # envelope, outbox, consumer idempotency, DLQ
│   ├── common-security/         # resource server config, permissions, token exchange client
│   ├── common-observability/
│   ├── event-schemas/           # Avro .avsc + generated classes
│   └── test-support/            # Testcontainers fixtures
├── services/
│   ├── api-gateway/  customer-service/  event-ingestion-service/  stream-processor/
│   ├── segmentation-service/  offer-service/  campaign-service/  personalization-service/
│   ├── notification-service/  analytics-service/  audit-service/
│   ├── ai-gateway/  rag-service/                     # Java
│   └── ai-orchestrator/  evaluation-service/         # Python (uv workspace)
├── mcp/
│   ├── marketing-mcp-server/
│   └── customer-mcp-server/
├── ai/                          # versioned AI assets (not code)
│   ├── prompts/                 # <promptId>/<version>/{prompt.yaml, system.md, *.hbs, output.schema.json}
│   ├── guardrails/              # rule packs, thresholds
│   ├── datasets/                # golden datasets (*.jsonl)
│   └── eval-config/             # metric floors, tolerances
├── data/
│   ├── schemas/                 # DQ expectations, lake table definitions
│   ├── knowledge-corpus/        # synthetic policies, guidelines, FAQs
│   ├── generator/               # synthetic data generator (Java CLI)
│   └── pipelines/               # lake curation job, Kafka Connect configs
├── frontend/marketing-console/  # React + TypeScript
├── infrastructure/
│   ├── docker/                  # compose overrides, Keycloak realm, Grafana dashboards, OTel config
│   ├── helm/                    # library chart + per-service values
│   ├── kubernetes/              # kind config, cluster add-ons
│   └── terraform/               # modules/ + envs/{dev,staging,prod}
├── tests/
│   ├── e2e/  contract/  performance/  ai-evaluation/
├── docs/
├── docker-compose.yml
├── Makefile
└── .env.example
```

Deviations from the requested layout are deliberate. The ADRs record them:
- `ai/agents` and `ai/tools` are **code** and live in `ai-orchestrator` and the MCP servers. `ai/` holds versioned assets that change without code releases but pass the eval gate.
- `platform/` holds shared technical (Java) libraries.
- Python is used only for ai-orchestrator and evaluation-service ([ADR-022](docs/decisions/ADR-022-polyglot-java-python.md)).
- LocalStack replaces AWS APIs locally ([ADR-023](docs/decisions/ADR-023-localstack-local-aws.md)).
- Several requested services are consolidated ([ADR-001](docs/decisions/ADR-001-microservices.md)).
