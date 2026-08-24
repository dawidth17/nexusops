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

    model_config = SettingsConfigDict(
        env_prefix="OPSSIGHT_",
        case_sensitive=False,
    )


settings = Settings()