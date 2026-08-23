import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from opssight.config import settings
from opssight.logging_config import configure_logging


configure_logging(settings.log_level)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logger.info(
        "application_startup",
        extra={
            "service": settings.service_name,
            "environment": settings.environment,
        },
    )

    yield

    logger.info(
        "application_shutdown",
        extra={
            "service": settings.service_name,
            "environment": settings.environment,
        },
    )


app = FastAPI(
    title=settings.service_name,
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": settings.service_name,
        "environment": settings.environment,
    }