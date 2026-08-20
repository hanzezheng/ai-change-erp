from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AI_",
        env_file=".env",
        extra="ignore",
        protected_namespaces=("settings_",),
    )

    service_name: str = "nongpi-ai-service"
    # openai | stub
    model_provider: str = "stub"
    openai_compatible_base_url: str = "https://api.openai.com/v1"
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    asr_provider: str = "stub"


settings = Settings()
