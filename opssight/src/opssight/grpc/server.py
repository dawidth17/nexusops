from concurrent.futures import ThreadPoolExecutor

import grpc

from opssight.config import settings
from opssight.generated.nexusops.telemetry.v1 import telemetry_pb2_grpc
from opssight.grpc.security import (
    GrpcMtlsConfiguration,
    build_grpc_server_credentials,
    grpc_mtls_configuration_from_settings,
)
from opssight.grpc.telemetry_service import TelemetryService


def create_grpc_server(
    host: str | None = None,
    port: int | None = None,
    mtls_configuration: GrpcMtlsConfiguration | None = None,
) -> tuple[grpc.Server, int]:
    bind_host = (
        settings.grpc_host
        if host is None
        else host
    )

    bind_port = (
        settings.grpc_port
        if port is None
        else port
    )

    security_configuration = (
        grpc_mtls_configuration_from_settings()
        if mtls_configuration is None
        else mtls_configuration
    )

    server = grpc.server(
        ThreadPoolExecutor(
            max_workers=settings.grpc_max_workers
        )
    )

    telemetry_pb2_grpc.add_TelemetryServiceServicer_to_server(
        TelemetryService(
            allowed_agent_ids=(
                security_configuration.allowed_agent_ids
                if security_configuration.enabled
                else None
            )
        ),
        server,
    )

    address = (
        f"{bind_host}:{bind_port}"
    )

    if security_configuration.enabled:
        bound_port = server.add_secure_port(
            address,
            build_grpc_server_credentials(
                security_configuration
            ),
        )
    else:
        bound_port = server.add_insecure_port(
            address
        )

    if bound_port == 0:
        raise RuntimeError(
            f"unable to bind gRPC server to {address}"
        )

    return server, bound_port