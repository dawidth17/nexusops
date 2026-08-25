import asyncio
from datetime import UTC, datetime
from time import perf_counter

from opssight.checks.result import CheckResult


async def run_tcp_check(
    host: str,
    port: int,
    timeout_seconds: float = 5.0,
) -> CheckResult:
    started_at = datetime.now(UTC)
    start = perf_counter()

    try:
        _reader, writer = await asyncio.wait_for(
            asyncio.open_connection(host, port),
            timeout=timeout_seconds,
        )
    except TimeoutError:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"tcp connection timed out for {host}:{port}",
        )
    except OSError as error:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"tcp connection failed for {host}:{port}: {error}",
        )

    duration_ms = (perf_counter() - start) * 1000

    writer.close()
    await writer.wait_closed()

    return CheckResult(
        success=True,
        started_at=started_at,
        duration_ms=duration_ms,
        message=f"tcp connection succeeded for {host}:{port}",
    )