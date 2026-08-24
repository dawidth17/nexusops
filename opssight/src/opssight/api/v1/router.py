from fastapi import APIRouter

from opssight.api.v1.alerts import router as alerts_router
from opssight.api.v1.checks import router as checks_router
from opssight.api.v1.hosts import router as hosts_router
from opssight.api.v1.runbooks import router as runbooks_router
from opssight.api.v1.security_findings import (
    router as security_findings_router,
)


router = APIRouter()

router.include_router(
    hosts_router,
    prefix="/hosts",
    tags=["hosts"],
)

router.include_router(
    checks_router,
    prefix="/checks",
    tags=["checks"],
)

router.include_router(
    alerts_router,
    prefix="/alerts",
    tags=["alerts"],
)

router.include_router(
    security_findings_router,
    prefix="/security/findings",
    tags=["security"],
)

router.include_router(
    runbooks_router,
    prefix="/runbooks",
    tags=["runbooks"],
)