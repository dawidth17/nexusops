from uuid import uuid4

from fastapi.testclient import TestClient
from prometheus_client import REGISTRY

from opssight.main import app
from opssight.metrics import (
    record_alert_transition,
    record_check_execution,
    record_scheduler_dispatch,
    record_security_finding_change,
    record_security_signal,
)


def get_metric_value(
    name: str,
    labels: dict[str, str] | None = None,
) -> float:
    value = REGISTRY.get_sample_value(
        name,
        labels,
    )

    if value is None:
        return 0.0

    return value


def test_check_execution_counter() -> None:
    labels = {
        "check_type": "dns",
        "outcome": "success",
    }

    before = get_metric_value(
        "opssight_check_executions_total",
        labels,
    )

    record_check_execution(
        check_type="dns",
        success=True,
        duration_ms=25.0,
    )

    after = get_metric_value(
        "opssight_check_executions_total",
        labels,
    )

    assert after == before + 1


def test_check_execution_failure_counter() -> None:
    labels = {
        "check_type": "http",
        "outcome": "failure",
    }

    before = get_metric_value(
        "opssight_check_executions_total",
        labels,
    )

    record_check_execution(
        check_type="http",
        success=False,
        duration_ms=50.0,
    )

    after = get_metric_value(
        "opssight_check_executions_total",
        labels,
    )

    assert after == before + 1


def test_check_duration_histogram() -> None:
    labels = {
        "check_type": "tcp",
    }

    before = get_metric_value(
        "opssight_check_duration_seconds_count",
        labels,
    )

    record_check_execution(
        check_type="tcp",
        success=True,
        duration_ms=100.0,
    )

    after = get_metric_value(
        "opssight_check_duration_seconds_count",
        labels,
    )

    assert after == before + 1


def test_alert_transition_counter() -> None:
    labels = {
        "action": "open",
        "severity": "critical",
    }

    before = get_metric_value(
        "opssight_alert_transitions_total",
        labels,
    )

    record_alert_transition(
        action="open",
        severity="critical",
    )

    after = get_metric_value(
        "opssight_alert_transitions_total",
        labels,
    )

    assert after == before + 1


def test_security_signal_counter() -> None:
    labels = {
        "signal_type": "authentication_failure",
    }

    before = get_metric_value(
        "opssight_security_signals_total",
        labels,
    )

    record_security_signal(
        signal_type="authentication_failure",
    )

    after = get_metric_value(
        "opssight_security_signals_total",
        labels,
    )

    assert after == before + 1


def test_security_finding_change_counter() -> None:
    labels = {
        "action": "resolve",
        "severity": "warning",
    }

    before = get_metric_value(
        "opssight_security_finding_changes_total",
        labels,
    )

    record_security_finding_change(
        action="resolve",
        severity="warning",
    )

    after = get_metric_value(
        "opssight_security_finding_changes_total",
        labels,
    )

    assert after == before + 1


def test_scheduler_dispatch_counter() -> None:
    before = get_metric_value(
        "opssight_scheduler_dispatched_checks_total"
    )

    record_scheduler_dispatch()

    after = get_metric_value(
        "opssight_scheduler_dispatched_checks_total"
    )

    assert after == before + 1


def test_http_metrics_use_route_template() -> None:
    host_id = uuid4()

    labels = {
        "method": "GET",
        "path": "/api/v1/hosts/{host_id}",
        "status_code": "401",
    }

    before = get_metric_value(
        "opssight_http_requests_total",
        labels,
    )

    with TestClient(app) as client:
        response = client.get(
            f"/api/v1/hosts/{host_id}"
        )

    after = get_metric_value(
        "opssight_http_requests_total",
        labels,
    )

    assert response.status_code == 401
    assert after == before + 1


def test_unmatched_routes_do_not_use_raw_path() -> None:
    random_path = f"/missing-{uuid4()}"

    labels = {
        "method": "GET",
        "path": "unmatched",
        "status_code": "404",
    }

    before = get_metric_value(
        "opssight_http_requests_total",
        labels,
    )

    with TestClient(app) as client:
        response = client.get(
            random_path
        )

    after = get_metric_value(
        "opssight_http_requests_total",
        labels,
    )

    assert response.status_code == 404
    assert after == before + 1

    raw_path_value = get_metric_value(
        "opssight_http_requests_total",
        {
            "method": "GET",
            "path": random_path,
            "status_code": "404",
        },
    )

    assert raw_path_value == 0


def test_metrics_endpoint_is_not_instrumented() -> None:
    labels = {
        "method": "GET",
        "path": "/metrics",
        "status_code": "200",
    }

    before = get_metric_value(
        "opssight_http_requests_total",
        labels,
    )

    with TestClient(app) as client:
        response = client.get(
            "/metrics"
        )

    after = get_metric_value(
        "opssight_http_requests_total",
        labels,
    )

    assert response.status_code == 200
    assert after == before