import re
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path

import grpc

from opssight.config import settings

_AGENT_ID_PATTERN = re.compile(
    r"[A-Za-z0-9][A-Za-z0-9._-]{0,199}"
)


@dataclass(frozen=True)
class GrpcMtlsConfiguration:
    enabled: bool = False

    ca_certificate_path: Path | None = None
    server_certificate_path: Path | None = None
    server_private_key_path: Path | None = None

    allowed_agent_ids: frozenset[str] = frozenset()


class GrpcClientIdentityError(Exception):
    def __init__(
        self,
        status_code: grpc.StatusCode,
        message: str,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code


def _optional_path(
    value: str | None,
) -> Path | None:
    if value is None:
        return None

    stripped = value.strip()

    if not stripped:
        return None

    return Path(stripped)


def parse_allowed_agent_ids(
    value: str,
) -> frozenset[str]:
    agent_ids = frozenset(
        item.strip()
        for item in value.split(",")
        if item.strip()
    )

    for agent_id in agent_ids:
        if _AGENT_ID_PATTERN.fullmatch(
            agent_id
        ) is None:
            raise RuntimeError(
                "invalid agent id in gRPC mTLS allowlist"
            )

    return agent_ids


def grpc_mtls_configuration_from_settings() -> GrpcMtlsConfiguration:
    return GrpcMtlsConfiguration(
        enabled=settings.grpc_mtls_enabled,
        ca_certificate_path=_optional_path(
            settings.grpc_mtls_ca_certificate_path
        ),
        server_certificate_path=_optional_path(
            settings.grpc_mtls_server_certificate_path
        ),
        server_private_key_path=_optional_path(
            settings.grpc_mtls_server_private_key_path
        ),
        allowed_agent_ids=parse_allowed_agent_ids(
            settings.grpc_mtls_allowed_agent_ids
        ),
    )


def _require_path(
    path: Path | None,
    description: str,
) -> Path:
    if path is None:
        raise RuntimeError(
            f"{description} is required when gRPC mTLS is enabled"
        )

    return path


def validate_grpc_mtls_configuration(
    configuration: GrpcMtlsConfiguration,
) -> None:
    if not configuration.enabled:
        return

    _require_path(
        configuration.ca_certificate_path,
        "gRPC mTLS CA certificate path",
    )

    _require_path(
        configuration.server_certificate_path,
        "gRPC mTLS server certificate path",
    )

    _require_path(
        configuration.server_private_key_path,
        "gRPC mTLS server private key path",
    )

    if not configuration.allowed_agent_ids:
        raise RuntimeError(
            "gRPC mTLS requires at least one allowed agent id"
        )


def _read_required_file(
    path: Path,
    description: str,
) -> bytes:
    try:
        content = path.read_bytes()
    except OSError as exc:
        raise RuntimeError(
            f"unable to read {description}: {path}"
        ) from exc

    if not content:
        raise RuntimeError(
            f"{description} is empty: {path}"
        )

    return content


def build_grpc_server_credentials(
    configuration: GrpcMtlsConfiguration,
) -> grpc.ServerCredentials:
    validate_grpc_mtls_configuration(
        configuration
    )

    if not configuration.enabled:
        raise RuntimeError(
            "cannot create mTLS credentials while mTLS is disabled"
        )

    ca_path = _require_path(
        configuration.ca_certificate_path,
        "gRPC mTLS CA certificate path",
    )

    certificate_path = _require_path(
        configuration.server_certificate_path,
        "gRPC mTLS server certificate path",
    )

    private_key_path = _require_path(
        configuration.server_private_key_path,
        "gRPC mTLS server private key path",
    )

    ca_certificate = _read_required_file(
        ca_path,
        "gRPC mTLS CA certificate",
    )

    server_certificate = _read_required_file(
        certificate_path,
        "gRPC mTLS server certificate",
    )

    server_private_key = _read_required_file(
        private_key_path,
        "gRPC mTLS server private key",
    )

    return grpc.ssl_server_credentials(
        private_key_certificate_chain_pairs=[
            (
                server_private_key,
                server_certificate,
            )
        ],
        root_certificates=ca_certificate,
        require_client_auth=True,
    )


def _normalize_auth_context_values(
    value: object,
) -> tuple[bytes, ...]:
    if isinstance(value, bytes):
        return (value,)

    if not isinstance(
        value,
        Iterable,
    ):
        return ()

    values: list[bytes] = []

    for item in value:
        if not isinstance(
            item,
            bytes,
        ):
            return ()

        values.append(
            item
        )

    return tuple(
        values
    )


def resolve_authenticated_agent_id(
    context: grpc.ServicerContext,
    allowed_agent_ids: frozenset[str],
) -> str:
    auth_context = context.auth_context()

    common_names = _normalize_auth_context_values(
        auth_context.get(
            "x509_common_name",
            (),
        )
    )

    if len(common_names) != 1:
        raise GrpcClientIdentityError(
            grpc.StatusCode.UNAUTHENTICATED,
            "client certificate identity is required",
        )

    try:
        agent_id = common_names[0].decode(
            "utf-8"
        )
    except UnicodeDecodeError as exc:
        raise GrpcClientIdentityError(
            grpc.StatusCode.UNAUTHENTICATED,
            "client certificate identity is invalid",
        ) from exc

    if (
        agent_id != agent_id.strip()
        or _AGENT_ID_PATTERN.fullmatch(
            agent_id
        ) is None
    ):
        raise GrpcClientIdentityError(
            grpc.StatusCode.UNAUTHENTICATED,
            "client certificate identity is invalid",
        )

    if agent_id not in allowed_agent_ids:
        raise GrpcClientIdentityError(
            grpc.StatusCode.PERMISSION_DENIED,
            "client certificate identity is not enrolled",
        )

    return agent_id