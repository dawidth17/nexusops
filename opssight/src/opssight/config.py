from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service_name: str = "OpsSight"
    environment: str = "development"
    log_level: str = "INFO"

    db_host: str = "127.0.0.1"
    db_port: int = 5434
    db_name: str = "opssight"
    db_user: str = "opssight"
    db_password: SecretStr = SecretStr("change_me")

    rabbitmq_host: str = "127.0.0.1"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "opssight"
    rabbitmq_password: SecretStr = SecretStr("change_me")

    kafka_bootstrap_servers: str = "127.0.0.1:9092"
    kafka_alert_topic: str = (
        "nexusops.opssight.alert-lifecycle.v1"
    )
    kafka_delivery_timeout_seconds: float = 10.0

    outbox_batch_size: int = 100
    outbox_retry_base_seconds: int = 5
    outbox_retry_max_seconds: int = 300

    grpc_host: str = "127.0.0.1"
    grpc_port: int = 50051
    grpc_max_workers: int = 10

    grpc_mtls_enabled: bool = False
    grpc_mtls_ca_certificate_path: str | None = None
    grpc_mtls_server_certificate_path: str | None = None
    grpc_mtls_server_private_key_path: str | None = None
    grpc_mtls_allowed_agent_ids: str = ""

    oidc_enabled: bool = False
    oidc_issuer: str = (
        "http://127.0.0.1:8081/realms/nexusops"
    )
    oidc_jwks_url: str = (
        "http://127.0.0.1:8081/realms/"
        "nexusops/protocol/openid-connect/certs"
    )
    oidc_client_id: str = "opssight"

    model_config = SettingsConfigDict(
        env_prefix="OPSSIGHT_",
        case_sensitive=False,
    )


settings = Settings()