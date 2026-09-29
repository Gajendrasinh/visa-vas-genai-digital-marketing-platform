"""Environment-driven configuration. Secrets are never defaulted here."""

from functools import lru_cache

from pydantic import AnyHttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ORCHESTRATOR_", extra="ignore")

    service_name: str = "ai-orchestrator"
    environment: str = "local"
    log_level: str = "INFO"
    ai_gateway_url: AnyHttpUrl = AnyHttpUrl("http://localhost:8090")


@lru_cache
def get_settings() -> Settings:
    return Settings()
