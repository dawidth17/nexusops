import asyncio
import socket
import ssl
from dataclasses import FrozenInstanceError
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest

from opssight.checks.dns import run_dns_check
from opssight.checks.http import run_http_check
from opssight.checks.result import CheckResult
from opssight.checks.tcp import run_tcp_check
from opssight.checks.tls import run_tls_check


class FakeAsyncClient:
    status_code = 200
    error: Exception | None = None

    def __init__(self, **kwargs: object) -> None:
        pass

    async def __aenter__(self) -> "FakeAsyncClient":
        return self

    async def __aexit__(
        self,
        exc_type: object,
        exc_value: object,
        traceback: object,
    ) -> None:
        pass

    async def get(self, url: str) -> object:
        if self.error is not None:
            raise self.error

        return SimpleNamespace(status_code=self.status_code)


class FakeSslObject:
    def getpeercert(self) -> dict[str, str]:
        return {
            "notAfter": "Sep 01 00:00:00 2030 GMT",
        }


class FakeWriter:
    def __init__(self) -> None:
        self.closed = False

    def get_extra_info(self, name: str) -> object | None:
        if name == "ssl_object":
            return FakeSslObject()

        return None

    def close(self) -> None:
        self.closed = True

    async def wait_closed(self) -> None:
        pass


def test_check_result_is_immutable() -> None:
    result = CheckResult(
        success=True,
        started_at=datetime.now(UTC),
        duration_ms=10.0,
        message="check succeeded",
    )

    with pytest.raises(FrozenInstanceError):
        result.success = False


def test_dns_check_succeeds(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    getaddrinfo = AsyncMock(
        return_value=[
            (
                socket.AF_INET,
                socket.SOCK_STREAM,
                socket.IPPROTO_TCP,
                "",
                ("127.0.0.1", 0),
            ),
        ]
    )

    monkeypatch.setattr(
        asyncio.BaseEventLoop,
        "getaddrinfo",
        getaddrinfo,
    )

    result = asyncio.run(
        run_dns_check("test.local")
    )

    assert result.success is True
    assert "127.0.0.1" in result.message
    assert result.duration_ms >= 0


def test_dns_check_handles_resolution_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    getaddrinfo = AsyncMock(
        side_effect=socket.gaierror(
            socket.EAI_NONAME,
            "name or service not known",
        )
    )

    monkeypatch.setattr(
        asyncio.BaseEventLoop,
        "getaddrinfo",
        getaddrinfo,
    )

    result = asyncio.run(
        run_dns_check("missing.test")
    )

    assert result.success is False
    assert "dns resolution failed" in result.message


def test_dns_check_handles_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def slow_getaddrinfo(
        *args: object,
        **kwargs: object,
    ) -> list[object]:
        await asyncio.sleep(1)
        return []

    monkeypatch.setattr(
        asyncio.BaseEventLoop,
        "getaddrinfo",
        slow_getaddrinfo,
    )

    result = asyncio.run(
        run_dns_check(
            "slow.test",
            timeout_seconds=0.01,
        )
    )

    assert result.success is False
    assert "timed out" in result.message


def test_tcp_check_succeeds() -> None:
    async def scenario() -> CheckResult:
        async def handle_connection(
            reader: asyncio.StreamReader,
            writer: asyncio.StreamWriter,
        ) -> None:
            writer.close()
            await writer.wait_closed()

        server = await asyncio.start_server(
            handle_connection,
            "127.0.0.1",
            0,
        )

        port = server.sockets[0].getsockname()[1]

        try:
            return await run_tcp_check(
                "127.0.0.1",
                port,
            )
        finally:
            server.close()
            await server.wait_closed()

    result = asyncio.run(scenario())

    assert result.success is True
    assert "tcp connection succeeded" in result.message


def test_tcp_check_handles_connection_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    open_connection = AsyncMock(
        side_effect=ConnectionRefusedError(
            "connection refused"
        )
    )

    monkeypatch.setattr(
        asyncio,
        "open_connection",
        open_connection,
    )

    result = asyncio.run(
        run_tcp_check(
            "127.0.0.1",
            9999,
        )
    )

    assert result.success is False
    assert "tcp connection failed" in result.message


def test_tcp_check_handles_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def slow_connection(
        *args: object,
        **kwargs: object,
    ) -> tuple[object, object]:
        await asyncio.sleep(1)
        return object(), object()

    monkeypatch.setattr(
        asyncio,
        "open_connection",
        slow_connection,
    )

    result = asyncio.run(
        run_tcp_check(
            "127.0.0.1",
            9999,
            timeout_seconds=0.01,
        )
    )

    assert result.success is False
    assert "timed out" in result.message


def test_http_check_succeeds(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    FakeAsyncClient.status_code = 200
    FakeAsyncClient.error = None

    monkeypatch.setattr(
        httpx,
        "AsyncClient",
        FakeAsyncClient,
    )

    result = asyncio.run(
        run_http_check(
            "https://service.test/health"
        )
    )

    assert result.success is True
    assert "200" in result.message


def test_http_check_handles_error_status(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    FakeAsyncClient.status_code = 503
    FakeAsyncClient.error = None

    monkeypatch.setattr(
        httpx,
        "AsyncClient",
        FakeAsyncClient,
    )

    result = asyncio.run(
        run_http_check(
            "https://service.test/health"
        )
    )

    assert result.success is False
    assert "503" in result.message


def test_http_check_handles_request_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    request = httpx.Request(
        "GET",
        "https://service.test/health",
    )

    FakeAsyncClient.error = httpx.ConnectError(
        "connection failed",
        request=request,
    )

    monkeypatch.setattr(
        httpx,
        "AsyncClient",
        FakeAsyncClient,
    )

    result = asyncio.run(
        run_http_check(
            "https://service.test/health"
        )
    )

    FakeAsyncClient.error = None

    assert result.success is False
    assert "http request failed" in result.message


def test_http_check_handles_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    request = httpx.Request(
        "GET",
        "https://service.test/health",
    )

    FakeAsyncClient.error = httpx.ReadTimeout(
        "request timed out",
        request=request,
    )

    monkeypatch.setattr(
        httpx,
        "AsyncClient",
        FakeAsyncClient,
    )

    result = asyncio.run(
        run_http_check(
            "https://service.test/health"
        )
    )

    FakeAsyncClient.error = None

    assert result.success is False
    assert "timed out" in result.message


def test_tls_check_succeeds(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    writer = FakeWriter()

    open_connection = AsyncMock(
        return_value=(
            object(),
            writer,
        )
    )

    monkeypatch.setattr(
        asyncio,
        "open_connection",
        open_connection,
    )

    result = asyncio.run(
        run_tls_check(
            "service.test",
            443,
        )
    )

    assert result.success is True
    assert "tls certificate valid" in result.message
    assert "expires" in result.message
    assert writer.closed is True


def test_tls_check_handles_certificate_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    open_connection = AsyncMock(
        side_effect=ssl.SSLCertVerificationError(
            1,
            "certificate verify failed",
        )
    )

    monkeypatch.setattr(
        asyncio,
        "open_connection",
        open_connection,
    )

    result = asyncio.run(
        run_tls_check(
            "service.test",
            443,
        )
    )

    assert result.success is False
    assert "certificate verification failed" in result.message


def test_tls_check_handles_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def slow_connection(
        *args: object,
        **kwargs: object,
    ) -> tuple[object, object]:
        await asyncio.sleep(1)
        return object(), object()

    monkeypatch.setattr(
        asyncio,
        "open_connection",
        slow_connection,
    )

    result = asyncio.run(
        run_tls_check(
            "service.test",
            443,
            timeout_seconds=0.01,
        )
    )

    assert result.success is False
    assert "timed out" in result.message