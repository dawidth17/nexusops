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

    grpc_host: str = "127.0.0.1"
    grpc_port: int = 50051
    grpc_max_workers: int = 10

    grpc_mtls_enabled: bool = False
    grpc_mtls_ca_certificate_path: str | None = None
    grpc_mtls_server_certificate_path: str | None = None
    grpc_mtls_server_private_key_path: str | None = None
    grpc_mtls_allowed_agent_ids: str = ""

    model_config = SettingsConfigDict(
        env_prefix="OPSSIGHT_",
        case_sensitive=False,
    )


settings = Settings()