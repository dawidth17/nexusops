from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import require_role
from opssight.auth.models import Principal, Role
from opssight.repositories.alert_repository import (
    get_alert_by_id,
    list_alerts,
)
from opssight.schemas.alert import AlertResponse


router = APIRouter()


@router.get(
    "",
    response_model=list[AlertResponse],
)
def get_alerts(
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> list[AlertResponse]:
    alerts = list_alerts(
        session
    )

    return [
        AlertResponse.model_validate(alert)
        for alert in alerts
    ]


@router.get(
    "/{alert_id}",
    response_model=AlertResponse,
)
def get_alert_by_id_endpoint(
    alert_id: UUID,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> AlertResponse:
    alert = get_alert_by_id(
        session,
        alert_id,
    )

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="alert not found",
        )

    return AlertResponse.model_validate(
        alert
    )