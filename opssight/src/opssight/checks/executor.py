from urllib.parse import urlsplit

from opssight.checks.dns import run_dns_check
from opssight.checks.http import run_http_check
from opssight.checks.result import CheckResult
from opssight.checks.tcp import run_tcp_check
from opssight.checks.tls import run_tls_check
from opssight.models.check import Check


def parse_host_port(
    target: str,
    default_port: int | None = None,
) -> tuple[str, int]:
    parsed = urlsplit(f"//{target}")

    host = parsed.hostname

    if host is None:
        raise ValueError(
            f"invalid network target: {target}"
        )

    try:
        port = parsed.port
    except ValueError as error:
        raise ValueError(
            f"invalid network target: {target}"
        ) from error

    if port is None:
        port = default_port

    if port is None:
        raise ValueError(
            f"port is required for target: {target}"
        )

    return host, port


async def execute_check(
    check: Check,
) -> CheckResult:
    check_type = check.check_type.lower()
    timeout_seconds = float(check.timeout_seconds)

    if check_type == "dns":
        return await run_dns_check(
            check.target,
            timeout_seconds=timeout_seconds,
        )

    if check_type == "tcp":
        host, port = parse_host_port(
            check.target,
        )

        return await run_tcp_check(
            host,
            port,
            timeout_seconds=timeout_seconds,
        )

    if check_type in {"http", "https"}:
        return await run_http_check(
            check.target,
            timeout_seconds=timeout_seconds,
        )

    if check_type == "tls":
        host, port = parse_host_port(
            check.target,
            default_port=443,
        )

        return await run_tls_check(
            host,
            port,
            timeout_seconds=timeout_seconds,
        )

    raise ValueError(
        f"unsupported check type: {check.check_type}"
    )