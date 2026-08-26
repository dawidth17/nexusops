import logging
import time
from collections.abc import Awaitable, Callable

from fastapi import Request, Response
from prometheus_client import (
    REGISTRY,
    Counter,
    Histogram,
)
from prometheus_client.core import GaugeMetricFamily
from sqlalchemy import func, select
from sqlalchemy.exc import SQLAlchemyError

from opssight.database import SessionFactory
from opssight.models import (
    Alert,
    OutboxEvent,
    SecurityFinding,
)

logger = logging.getLogger(__name__)


HTTP_REQUESTS_TOTAL = Counter(
    "opssight_http_requests_total",
    "Total number of HTTP requests",
    [
        "method",
        "path",
        "status_code",
    ],
)

HTTP_REQUEST_DURATION_SECONDS = Histogram(
    "opssight_http_request_duration_seconds",
    "HTTP request duration in seconds",
    [
        "method",
        "path",
    ],
)

CHECK_EXECUTIONS_TOTAL = Counter(
    "opssight_check_executions_total",
    "Total number of monitoring checks executed",
    [
        "check_type",
        "outcome",
    ],
)

CHECK_DURATION_SECONDS = Histogram(
    "opssight_check_duration_seconds",
    "Monitoring check duration in seconds",
    [
        "check_type",
    ],
)

ALERT_TRANSITIONS_TOTAL = Counter(
    "opssight_alert_transitions_total",
    "Total number of monitoring alert state transitions",
    [
        "action",
        "severity",
    ],
)

SECURITY_SIGNALS_TOTAL = Counter(
    "opssight_security_signals_total",
    "Total number of security signals processed",
    [
        "signal_type",
    ],
)

SECURITY_FINDING_CHANGES_TOTAL = Counter(
    "opssight_security_finding_changes_total",
    "Total number of security finding changes",
    [
        "action",
        "severity",
    ],
)

SCHEDULER_DISPATCHED_CHECKS_TOTAL = Counter(
    "opssight_scheduler_dispatched_checks_total",
    "Total number of checks dispatched by the scheduler",
)

OUTBOX_PUBLICATIONS_TOTAL = Counter(
    "opssight_outbox_publications_total",
    "Total number of outbox publication attempts",
    [
        "event_type",
        "outcome",
    ],
)


class OpsSightStateCollector:
    def describe(self):
        yield GaugeMetricFamily(
            "opssight_open_alerts",
            "Current number of open monitoring alerts",
        )

        yield GaugeMetricFamily(
            "opssight_open_security_findings",
            "Current number of open security findings",
        )

        yield GaugeMetricFamily(
            "opssight_outbox_pending_events",
            "Current number of unpublished outbox events",
        )

        yield GaugeMetricFamily(
            "opssight_state_metrics_collection_success",
            "Whether OpsSight state metrics were collected successfully",
        )

    def collect(self):
        collection_success = GaugeMetricFamily(
            "opssight_state_metrics_collection_success",
            "Whether OpsSight state metrics were collected successfully",
        )

        try:
            with SessionFactory() as session:
                open_alerts = session.scalar(
                    select(
                        func.count()
                    )
                    .select_from(
                        Alert
                    )
                    .where(
                        Alert.status == "open"
                    )
                )

                open_security_findings = session.scalar(
                    select(
                        func.count()
                    )
                    .select_from(
                        SecurityFinding
                    )
                    .where(
                        SecurityFinding.status == "open"
                    )
                )

                pending_outbox_events = session.scalar(
                    select(
                        func.count()
                    )
                    .select_from(
                        OutboxEvent
                    )
                    .where(
                        OutboxEvent.published_at.is_(None)
                    )
                )

        except SQLAlchemyError:
            logger.warning(
                "state_metrics_collection_failed",
                exc_info=True,
            )

            collection_success.add_metric(
                [],
                0,
            )

            yield collection_success

            return

        open_alerts_metric = GaugeMetricFamily(
            "opssight_open_alerts",
            "Current number of open monitoring alerts",
        )

        open_alerts_metric.add_metric(
            [],
            float(
                open_alerts or 0
            ),
        )

        open_findings_metric = GaugeMetricFamily(
            "opssight_open_security_findings",
            "Current number of open security findings",
        )

        open_findings_metric.add_metric(
            [],
            float(
                open_security_findings or 0
            ),
        )

        pending_outbox_metric = GaugeMetricFamily(
            "opssight_outbox_pending_events",
            "Current number of unpublished outbox events",
        )

        pending_outbox_metric.add_metric(
            [],
            float(
                pending_outbox_events or 0
            ),
        )

        collection_success.add_metric(
            [],
            1,
        )

        yield open_alerts_metric
        yield open_findings_metric
        yield pending_outbox_metric
        yield collection_success


STATE_COLLECTOR = OpsSightStateCollector()

REGISTRY.register(
    STATE_COLLECTOR
)


def record_check_execution(
    check_type: str,
    success: bool,
    duration_ms: float,
) -> None:
    outcome = (
        "success"
        if success
        else "failure"
    )

    CHECK_EXECUTIONS_TOTAL.labels(
        check_type=check_type,
        outcome=outcome,
    ).inc()

    CHECK_DURATION_SECONDS.labels(
        check_type=check_type,
    ).observe(
        duration_ms / 1000.0
    )


def record_alert_transition(
    action: str,
    severity: str,
) -> None:
    ALERT_TRANSITIONS_TOTAL.labels(
        action=action,
        severity=severity,
    ).inc()


def record_security_signal(
    signal_type: str,
) -> None:
    SECURITY_SIGNALS_TOTAL.labels(
        signal_type=signal_type,
    ).inc()


def record_security_finding_change(
    action: str,
    severity: str,
) -> None:
    SECURITY_FINDING_CHANGES_TOTAL.labels(
        action=action,
        severity=severity,
    ).inc()


def record_scheduler_dispatch() -> None:
    SCHEDULER_DISPATCHED_CHECKS_TOTAL.inc()


def record_outbox_publication(
    event_type: str,
    success: bool,
) -> None:
    outcome = (
        "success"
        if success
        else "failure"
    )

    OUTBOX_PUBLICATIONS_TOTAL.labels(
        event_type=event_type,
        outcome=outcome,
    ).inc()


def get_route_template(
    request: Request,
) -> str:
    scope = request.scope

    template: str | None = None

    fastapi_scope = scope.get(
        "fastapi"
    )

    if isinstance(
        fastapi_scope,
        dict,
    ):
        context = fastapi_scope.get(
            "effective_route_context"
        )

        if context is not None:
            template = getattr(
                context,
                "path",
                None,
            )

    if template is None:
        route = scope.get(
            "route"
        )

        template = getattr(
            route,
            "path",
            None,
        )

    if not template:
        return "unmatched"

    root_path = scope.get(
        "root_path",
        "",
    ).rstrip("/")

    if root_path:
        return f"{root_path}{template}"

    return template


async def record_http_metrics(
    request: Request,
    call_next: Callable[
        [Request],
        Awaitable[Response],
    ],
) -> Response:
    if request.url.path == "/metrics":
        return await call_next(
            request
        )

    start_time = time.perf_counter()
    status_code = "500"

    try:
        response = await call_next(
            request
        )

        status_code = str(
            response.status_code
        )

        return response
    finally:
        duration_seconds = (
            time.perf_counter() - start_time
        )

        path = get_route_template(
            request
        )

        HTTP_REQUESTS_TOTAL.labels(
            method=request.method,
            path=path,
            status_code=status_code,
        ).inc()

        HTTP_REQUEST_DURATION_SECONDS.labels(
            method=request.method,
            path=path,
        ).observe(
            duration_seconds
        )