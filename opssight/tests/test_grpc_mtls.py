import shutil
import subprocess
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

import grpc
import pytest
from google.protobuf.timestamp_pb2 import Timestamp
from sqlalchemy import select

from opssight.config import settings
from opssight.database import SessionFactory
from opssight.generated.nexusops.telemetry.v1 import (
    telemetry_pb2,
    telemetry_pb2_grpc,
)
from opssight.grpc.security import GrpcMtlsConfiguration
from opssight.grpc.server import create_grpc_server
from opssight.models.agent import Agent

pytestmark = [
    pytest.mark.skipif(
        not settings.db_name.endswith("_test"),
        reason="gRPC mTLS tests require a dedicated test database",
    ),
    pytest.mark.skipif(
        shutil.which("openssl") is None,
        reason="gRPC mTLS tests require OpenSSL",
    ),
]


@dataclass(frozen=True)
class MtlsMaterial:
    ca_certificate: Path
    server_certificate: Path
    server_private_key: Path

    client_certificate: Path
    client_private_key: Path

    unknown_client_certificate: Path
    unknown_client_private_key: Path

    rogue_client_certificate: Path
    rogue_client_private_key: Path


def _build_timestamp(
    value: datetime,
) -> Timestamp:
    timestamp = Timestamp()
    timestamp.FromDatetime(
        value
    )

    return timestamp


def _certificate_script() -> Path:
    return (
        Path(__file__)
        .resolve()
        .parents[2]
        / "infra"
        / "certs"
        / "dev-mtls.sh"
    )


def _run_certificate_command(
    *arguments: str,
) -> None:
    subprocess.run(
        [
            "bash",
            str(_certificate_script()),
            *arguments,
        ],
        check=True,
        capture_output=True,
        text=True,
    )


@pytest.fixture(scope="module")
def mtls_material(
    tmp_path_factory: pytest.TempPathFactory,
) -> MtlsMaterial:
    root = tmp_path_factory.mktemp(
        "opssight-mtls"
    )

    trusted_root = root / "trusted"

    _run_certificate_command(
        "bootstrap",
        "grpc-mtls-agent",
        str(trusted_root),
    )

    _run_certificate_command(
        "issue-agent",
        "unknown-agent",
        str(trusted_root),
    )

    rogue_root = root / "rogue"

    _run_certificate_command(
        "init-ca",
        str(rogue_root),
    )

    _run_certificate_command(
        "issue-agent",
        "grpc-mtls-agent",
        str(rogue_root),
    )

    return MtlsMaterial(
        ca_certificate=(
            trusted_root
            / "ca"
            / "ca.cert.pem"
        ),
        server_certificate=(
            trusted_root
            / "server"
            / "server.cert.pem"
        ),
        server_private_key=(
            trusted_root
            / "server"
            / "server.key.pem"
        ),
        client_certificate=(
            trusted_root
            / "agents"
            / "grpc-mtls-agent"
            / "client.cert.pem"
        ),
        client_private_key=(
            trusted_root
            / "agents"
            / "grpc-mtls-agent"
            / "client.key.pem"
        ),
        unknown_client_certificate=(
            trusted_root
            / "agents"
            / "unknown-agent"
            / "client.cert.pem"
        ),
        unknown_client_private_key=(
            trusted_root
            / "agents"
            / "unknown-agent"
            / "client.key.pem"
        ),
        rogue_client_certificate=(
            rogue_root
            / "agents"
            / "grpc-mtls-agent"
            / "client.cert.pem"
        ),
        rogue_client_private_key=(
            rogue_root
            / "agents"
            / "grpc-mtls-agent"
            / "client.key.pem"
        ),
    )


def _server_configuration(
    material: MtlsMaterial,
) -> GrpcMtlsConfiguration:
    return GrpcMtlsConfiguration(
        enabled=True,
        ca_certificate_path=material.ca_certificate,
        server_certificate_path=material.server_certificate,
        server_private_key_path=material.server_private_key,
        allowed_agent_ids=frozenset(
            {
                "grpc-mtls-agent",
            }
        ),
    )


def _client_credentials(
    material: MtlsMaterial,
    certificate: Path,
    private_key: Path,
) -> grpc.ChannelCredentials:
    return grpc.ssl_channel_credentials(
        root_certificates=(
            material.ca_certificate.read_bytes()
        ),
        private_key=private_key.read_bytes(),
        certificate_chain=certificate.read_bytes(),
    )


def _heartbeat(
    agent_id: str,
    batch_id: str,
) -> telemetry_pb2.TelemetryEnvelope:
    return telemetry_pb2.TelemetryEnvelope(
        agent_id=agent_id,
        batch_id=batch_id,
        sent_at=_build_timestamp(
            datetime.now(UTC)
        ),
        heartbeat=telemetry_pb2.Heartbeat(
            hostname="grpc-mtls-host",
            agent_version="0.1.0",
        ),
    )


def test_grpc_mtls_accepts_known_agent(
    mtls_material: MtlsMaterial,
) -> None:
    server, port = create_grpc_server(
        host="127.0.0.1",
        port=0,
        mtls_configuration=_server_configuration(
            mtls_material
        ),
    )

    server.start()

    credentials = _client_credentials(
        mtls_material,
        mtls_material.client_certificate,
        mtls_material.client_private_key,
    )

    try:
        with grpc.secure_channel(
            f"127.0.0.1:{port}",
            credentials,
        ) as channel:
            grpc.channel_ready_future(
                channel
            ).result(
                timeout=5
            )

            stub = telemetry_pb2_grpc.TelemetryServiceStub(
                channel
            )

            responses = list(
                stub.StreamTelemetry(
                    iter(
                        [
                            _heartbeat(
                                "grpc-mtls-agent",
                                "mtls-heartbeat-1",
                            )
                        ]
                    ),
                    timeout=5,
                )
            )

            assert len(responses) == 1
            assert responses[0].HasField("ack")
            assert responses[0].ack.batch_id == "mtls-heartbeat-1"

    finally:
        server.stop(
            grace=0
        ).wait()

    with SessionFactory() as session:
        agent = session.scalar(
            select(Agent).where(
                Agent.agent_id == "grpc-mtls-agent"
            )
        )

        assert agent is not None
        assert agent.host.name == "grpc-mtls-host"


def test_grpc_mtls_rejects_payload_agent_spoofing(
    mtls_material: MtlsMaterial,
) -> None:
    server, port = create_grpc_server(
        host="127.0.0.1",
        port=0,
        mtls_configuration=_server_configuration(
            mtls_material
        ),
    )

    server.start()

    credentials = _client_credentials(
        mtls_material,
        mtls_material.client_certificate,
        mtls_material.client_private_key,
    )

    try:
        with grpc.secure_channel(
            f"127.0.0.1:{port}",
            credentials,
        ) as channel:
            grpc.channel_ready_future(
                channel
            ).result(
                timeout=5
            )

            stub = telemetry_pb2_grpc.TelemetryServiceStub(
                channel
            )

            with pytest.raises(
                grpc.RpcError
            ) as error:
                list(
                    stub.StreamTelemetry(
                        iter(
                            [
                                _heartbeat(
                                    "spoofed-agent",
                                    "spoofed-heartbeat",
                                )
                            ]
                        ),
                        timeout=5,
                    )
                )

            assert (
                error.value.code()
                == grpc.StatusCode.PERMISSION_DENIED
            )

    finally:
        server.stop(
            grace=0
        ).wait()


def test_grpc_mtls_rejects_unknown_signed_client(
    mtls_material: MtlsMaterial,
) -> None:
    server, port = create_grpc_server(
        host="127.0.0.1",
        port=0,
        mtls_configuration=_server_configuration(
            mtls_material
        ),
    )

    server.start()

    credentials = _client_credentials(
        mtls_material,
        mtls_material.unknown_client_certificate,
        mtls_material.unknown_client_private_key,
    )

    try:
        with grpc.secure_channel(
            f"127.0.0.1:{port}",
            credentials,
        ) as channel:
            grpc.channel_ready_future(
                channel
            ).result(
                timeout=5
            )

            stub = telemetry_pb2_grpc.TelemetryServiceStub(
                channel
            )

            with pytest.raises(
                grpc.RpcError
            ) as error:
                list(
                    stub.StreamTelemetry(
                        iter(
                            [
                                _heartbeat(
                                    "unknown-agent",
                                    "unknown-heartbeat",
                                )
                            ]
                        ),
                        timeout=5,
                    )
                )

            assert (
                error.value.code()
                == grpc.StatusCode.PERMISSION_DENIED
            )

    finally:
        server.stop(
            grace=0
        ).wait()


def test_grpc_mtls_rejects_client_from_unknown_ca(
    mtls_material: MtlsMaterial,
) -> None:
    server, port = create_grpc_server(
        host="127.0.0.1",
        port=0,
        mtls_configuration=_server_configuration(
            mtls_material
        ),
    )

    server.start()

    credentials = _client_credentials(
        mtls_material,
        mtls_material.rogue_client_certificate,
        mtls_material.rogue_client_private_key,
    )

    channel = grpc.secure_channel(
        f"127.0.0.1:{port}",
        credentials,
    )

    try:
        with pytest.raises(
            grpc.FutureTimeoutError
        ):
            grpc.channel_ready_future(
                channel
            ).result(
                timeout=2
            )

    finally:
        channel.close()

        server.stop(
            grace=0
        ).wait()