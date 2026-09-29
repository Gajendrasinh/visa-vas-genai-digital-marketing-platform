SHELL := /bin/bash
COMPOSE := docker compose --env-file .env

.DEFAULT_GOAL := help
.PHONY: help env build test test-java test-python lint fmt security-scan up-core up-aws down ps logs clean

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n",$$1,$$2}'

env: ## Create .env, or add settings missing from it (never overwrites existing values)
	@./scripts/sync-env.sh

build: test ## Full build: compile, tests, static analysis, coverage gates (Java + Python)

test: test-java test-python ## Run all tests and quality gates

test-java: ## Maven verify (Spotless, SpotBugs, JaCoCo, enforcer, ArchUnit)
	./mvnw -B verify

test-python: ## ruff, mypy (strict), pytest with coverage gate
	uv sync --all-packages --locked
	uv run ruff check .
	uv run ruff format --check .
	uv run mypy services/ai-orchestrator/src services/ai-orchestrator/tests
	cd services/ai-orchestrator && uv run pytest

lint: ## Static checks only
	./mvnw -B -q spotless:check
	uv run ruff check . && uv run ruff format --check .

fmt: ## Apply formatters
	./mvnw -B -q spotless:apply
	uv run ruff format . && uv run ruff check --fix .

security-scan: ## OWASP dependency-check (set NVD_API_KEY for reasonable speed)
	./mvnw -B -Psecurity verify -DskipTests

up-core: env ## Start core infrastructure (+ LocalStack if LOCALSTACK_AUTH_TOKEN is set)
	@if grep -qE '^LOCALSTACK_AUTH_TOKEN=.+' .env; then \
	  $(COMPOSE) --profile core --profile aws up -d --wait; \
	else \
	  echo "LOCALSTACK_AUTH_TOKEN not set in .env: starting without LocalStack (see ADR-023)"; \
	  $(COMPOSE) --profile core up -d --wait; \
	fi
	@$(COMPOSE) --profile core logs kafka-init | tail -n 20

up-aws: env ## Start only LocalStack
	$(COMPOSE) --profile aws up -d --wait

down: ## Stop all containers (keeps volumes)
	$(COMPOSE) --profile core --profile aws down

ps: ## Show container status
	$(COMPOSE) --profile core --profile aws ps

logs: ## Tail logs (SERVICE=name)
	$(COMPOSE) --profile core --profile aws logs -f $(SERVICE)

clean: ## Stop containers and delete volumes and build output
	$(COMPOSE) --profile core --profile aws down -v
	./mvnw -B -q clean
