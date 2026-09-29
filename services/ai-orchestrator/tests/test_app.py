import json
import logging
import uuid

import pytest
from fastapi.testclient import TestClient

from ai_orchestrator.app import create_app
from ai_orchestrator.correlation import HEADER, correlation_id_var, resolve
from ai_orchestrator.logging_config import JsonFormatter
from ai_orchestrator.settings import Settings


@pytest.fixture
def client() -> TestClient:
    return TestClient(create_app(Settings(environment="test")))


def test_health_endpoints_report_up(client: TestClient) -> None:
    assert client.get("/health/live").json() == {"status": "UP"}
    assert client.get("/health/ready").json() == {"status": "UP"}


def test_propagates_well_formed_correlation_id(client: TestClient) -> None:
    response = client.get("/health/live", headers={HEADER: "req-12345678"})
    assert response.headers[HEADER] == "req-12345678"


def test_generates_correlation_id_when_absent(client: TestClient) -> None:
    response = client.get("/health/live")
    assert uuid.UUID(response.headers[HEADER])


@pytest.mark.parametrize("candidate", ["short", "a" * 65, "abc\tinjected=true", None])
def test_resolve_replaces_invalid_values(candidate: str | None) -> None:
    resolved = resolve(candidate)
    assert resolved != candidate
    assert uuid.UUID(resolved)


def test_json_log_contains_correlation_id() -> None:
    formatter = JsonFormatter("ai-orchestrator", "test")
    record = logging.LogRecord("x", logging.INFO, __file__, 1, "hello", None, None)
    token = correlation_id_var.set("req-abcdefgh")
    try:
        entry = json.loads(formatter.format(record))
    finally:
        correlation_id_var.reset(token)
    assert entry["correlationId"] == "req-abcdefgh"
    assert entry["service"] == "ai-orchestrator"
    assert entry["message"] == "hello"
