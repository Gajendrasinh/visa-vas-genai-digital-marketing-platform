"""Correlation-ID propagation, mirroring platform/common-web in the Java services."""

import re
import uuid
from contextvars import ContextVar

from starlette.types import ASGIApp, Message, Receive, Scope, Send

HEADER = "X-Correlation-Id"
_HEADER_BYTES = HEADER.lower().encode("latin-1")
# Same format rule as the Java CorrelationId: anything else is replaced to prevent log injection.
_VALID = re.compile(r"^[A-Za-z0-9._-]{8,64}$")

correlation_id_var: ContextVar[str | None] = ContextVar("correlation_id", default=None)


def resolve(candidate: str | None) -> str:
    if candidate is not None and _VALID.fullmatch(candidate):
        return candidate
    return str(uuid.uuid4())


class CorrelationIdMiddleware:
    """Pure ASGI middleware, so it also works for streaming (SSE) responses."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        inbound = next(
            (v.decode("latin-1") for k, v in scope["headers"] if k == _HEADER_BYTES), None
        )
        correlation_id = resolve(inbound)
        token = correlation_id_var.set(correlation_id)

        async def send_with_header(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = [(k, v) for k, v in message.get("headers", []) if k != _HEADER_BYTES]
                headers.append((_HEADER_BYTES, correlation_id.encode("latin-1")))
                message["headers"] = headers
            await send(message)

        try:
            await self.app(scope, receive, send_with_header)
        finally:
            correlation_id_var.reset(token)
