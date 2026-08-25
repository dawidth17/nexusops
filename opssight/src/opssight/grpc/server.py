from concurrent.futures import ThreadPoolExecutor

import grpc

from opssight.config import settings
from opssight.generated.nexusops.telemetry.v1 import telemetry_pb2_grpc
from opssight.grpc.telemetry_service import TelemetryService


def create_grpc_server(
    host: str | None = None,
    port: int | None = None,
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

    server = grpc.server(
        ThreadPoolExecutor(
            max_workers=settings.grpc_max_workers
        )
    )

    telemetry_pb2_grpc.add_TelemetryServiceServicer_to_server(
        TelemetryService(),
        server,
    )

    address = (
        f"{bind_host}:{bind_port}"
    )

    bound_port = server.add_insecure_port(
        address
    )

    if bound_port == 0:
        raise RuntimeError(
            f"unable to bind gRPC server to {address}"
        )

    return server, bound_port