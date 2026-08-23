import asyncio
import socket
from datetime import UTC, datetime
from time import perf_counter

from opssight.checks.result import CheckResult


async def run_dns_check(
    host: str,
    timeout_seconds: float = 5.0,
) -> CheckResult:
    started_at = datetime.now(UTC)
    start = perf_counter()

    try:
        loop = asyncio.get_running_loop()

        results = await asyncio.wait_for(
            loop.getaddrinfo(
                host,
                None,
                type=socket.SOCK_STREAM,
            ),
            timeout=timeout_seconds,
        )
    except TimeoutError:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"dns resolution timed out for {host}",
        )
    except socket.gaierror as error:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"dns resolution failed for {host}: {error}",
        )

    duration_ms = (perf_counter() - start) * 1000

    addresses = sorted(
        {
            result[4][0]
            for result in results
        }
    )

    return CheckResult(
        success=True,
        started_at=started_at,
        duration_ms=duration_ms,
        message=(
            f"dns resolution succeeded for {host}: "
            f"{', '.join(addresses)}"
        ),
    )