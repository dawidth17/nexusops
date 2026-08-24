from datetime import UTC, datetime, timedelta
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.orm import Session

from opssight.models.host import Host
from opssight.repositories.telemetry_repository import (
    create_telemetry,
    list_telemetry_by_time_range,
)


def test_telemetry_is_stored_in_timescale_hypertable(
    integration_session: Session,
) -> None:
    hypertable = integration_session.execute(
        text(
            """
            SELECT hypertable_name
            FROM timescaledb_information.hypertables
            WHERE hypertable_name = 'telemetry'
            """
        )
    ).scalar_one_or_none()

    assert hypertable == "telemetry"


def test_telemetry_time_range_flow(
    integration_session: Session,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"integration-telemetry-host-{suffix}",
        address="127.0.0.1",
    )

    integration_session.add(host)
    integration_session.flush()

    start = datetime(
        2026,
        8,
        24,
        12,
        0,
        tzinfo=UTC,
    )

    before_start = start - timedelta(minutes=1)
    middle = start + timedelta(minutes=5)
    end = start + timedelta(minutes=10)

    create_telemetry(
        integration_session,
        host_id=host.id,
        captured_at=before_start,
        metric_name="cpu_usage_percent",
        value=10.0,
        unit="percent",
        labels={
            "source": "integration-test",
        },
    )

    telemetry_at_start = create_telemetry(
        integration_session,
        host_id=host.id,
        captured_at=start,
        metric_name="cpu_usage_percent",
        value=20.0,
        unit="percent",
        labels={
            "source": "integration-test",
        },
    )

    telemetry_in_middle = create_telemetry(
        integration_session,
        host_id=host.id,
        captured_at=middle,
        metric_name="cpu_usage_percent",
        value=30.0,
        unit="percent",
        labels={
            "source": "integration-test",
        },
    )

    create_telemetry(
        integration_session,
        host_id=host.id,
        captured_at=end,
        metric_name="cpu_usage_percent",
        value=40.0,
        unit="percent",
        labels={
            "source": "integration-test",
        },
    )

    telemetry = list_telemetry_by_time_range(
        integration_session,
        host_id=host.id,
        metric_name="cpu_usage_percent",
        start_time=start,
        end_time=end,
    )

    assert len(telemetry) == 2

    assert telemetry[0].id == telemetry_at_start.id
    assert telemetry[0].captured_at == start
    assert telemetry[0].value == 20.0
    assert telemetry[0].unit == "percent"
    assert telemetry[0].labels == {
        "source": "integration-test"
    }

    assert telemetry[1].id == telemetry_in_middle.id
    assert telemetry[1].captured_at == middle
    assert telemetry[1].value == 30.0