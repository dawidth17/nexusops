from datetime import UTC, datetime
from time import perf_counter

import httpx

from opssight.checks.result import CheckResult


async def run_http_check(
    url: str,
    timeout_seconds: float = 5.0,
) -> CheckResult:
    started_at = datetime.now(UTC)
    start = perf_counter()

    try:
        async with httpx.AsyncClient(
            timeout=timeout_seconds,
            follow_redirects=True,
        ) as client:
            response = await client.get(url)

    except httpx.TimeoutException:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"http request timed out for {url}",
        )

    except httpx.RequestError as error:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"http request failed for {url}: {error}",
        )

    duration_ms = (perf_counter() - start) * 1000

    success = 200 <= response.status_code < 400

    return CheckResult(
        success=success,
        started_at=started_at,
        duration_ms=duration_ms,
        message=(
            f"http request returned "
            f"{response.status_code} for {url}"
        ),
    )