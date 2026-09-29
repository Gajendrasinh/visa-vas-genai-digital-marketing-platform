"""FastAPI application factory."""

from fastapi import FastAPI

from ai_orchestrator.correlation import CorrelationIdMiddleware
from ai_orchestrator.logging_config import configure_logging
from ai_orchestrator.settings import Settings, get_settings


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.service_name, settings.environment, settings.log_level)

    app = FastAPI(title="ai-orchestrator", version="0.1.0")
    app.add_middleware(CorrelationIdMiddleware)

    @app.get("/health/live", tags=["health"])
    async def live() -> dict[str, str]:
        return {"status": "UP"}

    @app.get("/health/ready", tags=["health"])
    async def ready() -> dict[str, str]:
        # Dependency checks (ai-gateway, Redis, Postgres) are added with those clients in Phase 12.
        return {"status": "UP"}

    return app
