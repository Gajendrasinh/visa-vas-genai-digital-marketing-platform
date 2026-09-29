"""Structured JSON logging with the same core fields as the Java services."""

import json
import logging
from datetime import UTC, datetime

from ai_orchestrator.correlation import correlation_id_var


class JsonFormatter(logging.Formatter):
    def __init__(self, service: str, environment: str) -> None:
        super().__init__()
        self._service = service
        self._environment = environment

    def format(self, record: logging.LogRecord) -> str:
        entry: dict[str, object] = {
            "timestamp": datetime.fromtimestamp(record.created, UTC).isoformat(),
            "level": record.levelname,
            "service": self._service,
            "env": self._environment,
            "logger": record.name,
            "message": record.getMessage(),
        }
        correlation_id = correlation_id_var.get()
        if correlation_id is not None:
            entry["correlationId"] = correlation_id
        if record.exc_info:
            entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(entry)


def configure_logging(service: str, environment: str, level: str) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter(service, environment))
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(level)
