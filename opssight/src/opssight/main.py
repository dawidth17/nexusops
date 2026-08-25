import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from starlette.responses import Response

from opssight.api.v1.router import router as api_v1_router
from opssight.config import settings
from opssight.logging_config import configure_logging
from opssight.metrics import record_http_metrics

configure_logging(
    settings.log_level
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(
    _app: FastAPI,
) -> AsyncIterator[None]:
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

app.include_router(
    api_v1_router,
    prefix="/api/v1",
)

@app.middleware("http")
async def metrics_middleware(
    request: Request,
    call_next,
):
    return await record_http_metrics(
        request,
        call_next,
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": settings.service_name,
        "environment": settings.environment,
    }

@app.get(
    "/metrics",
    include_in_schema=False,
)
def metrics() -> Response:
    return Response(
        content=generate_latest(),
        media_type=CONTENT_TYPE_LATEST,
    )