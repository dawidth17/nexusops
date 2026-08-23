from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    service_name: str = "OpsSight"
    environment: str = "development"
    log_level: str = "INFO"
    
    model_config = SettingsConfigDict(
        env_prefix="OPSSIGHT_",
        case_sensitive=False,
    )

settings = Settings()