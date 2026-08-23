from opssight.config import Settings


def test_default_settings() -> None:
    settings = Settings()

    assert settings.service_name == "OpsSight"
    assert settings.environment == "development"
    assert settings.log_level == "INFO"


def test_settings_from_environment(monkeypatch) -> None:
    monkeypatch.setenv("OPSSIGHT_SERVICE_NAME", "OpsSight-Test")
    monkeypatch.setenv("OPSSIGHT_ENVIRONMENT", "test")
    monkeypatch.setenv("OPSSIGHT_LOG_LEVEL", "DEBUG")

    settings = Settings()

    assert settings.service_name == "OpsSight-Test"
    assert settings.environment == "test"
    assert settings.log_level == "DEBUG"