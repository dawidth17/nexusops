import asyncio
import ssl
from datetime import UTC, datetime
from time import perf_counter

from opssight.checks.result import CheckResult


async def run_tls_check(
    host: str,
    port: int = 443,
    timeout_seconds: float = 5.0,
) -> CheckResult:
    started_at = datetime.now(UTC)
    start = perf_counter()

    context = ssl.create_default_context()

    try:
        _reader, writer = await asyncio.wait_for(
            asyncio.open_connection(
                host,
                port,
                ssl=context,
                server_hostname=host,
            ),
            timeout=timeout_seconds,
        )

    except TimeoutError:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"tls connection timed out for {host}:{port}",
        )

    except ssl.SSLCertVerificationError as error:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=(
                f"tls certificate verification failed "
                f"for {host}:{port}: {error}"
            ),
        )

    except ssl.SSLError as error:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"tls handshake failed for {host}:{port}: {error}",
        )

    except OSError as error:
        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"tls connection failed for {host}:{port}: {error}",
        )

    ssl_object = writer.get_extra_info("ssl_object")

    if ssl_object is None:
        writer.close()
        await writer.wait_closed()

        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=f"tls session was not available for {host}:{port}",
        )

    certificate = ssl_object.getpeercert()
    not_after = certificate.get("notAfter")

    if not isinstance(not_after, str):
        writer.close()
        await writer.wait_closed()

        duration_ms = (perf_counter() - start) * 1000

        return CheckResult(
            success=False,
            started_at=started_at,
            duration_ms=duration_ms,
            message=(
                f"tls certificate expiration was not available "
                f"for {host}:{port}"
            ),
        )

    expires_at = datetime.fromtimestamp(
        ssl.cert_time_to_seconds(not_after),
        UTC,
    )

    remaining = expires_at - datetime.now(UTC)
    days_remaining = max(
        0,
        int(remaining.total_seconds() // 86400),
    )

    writer.close()
    await writer.wait_closed()

    duration_ms = (perf_counter() - start) * 1000

    return CheckResult(
        success=True,
        started_at=started_at,
        duration_ms=duration_ms,
        message=(
            f"tls certificate valid for {host}:{port}; "
            f"expires {expires_at.isoformat()} "
            f"({days_remaining} days remaining)"
        ),
    )