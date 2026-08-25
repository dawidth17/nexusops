from datetime import UTC, datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import require_role
from opssight.auth.models import Principal, Role
from opssight.repositories.security_finding_repository import (
    get_security_finding_by_id,
    list_security_findings,
)
from opssight.schemas.security_finding import (
    SecurityFindingResponse,
)
from opssight.security.engine import resolve_security_finding

router = APIRouter()


@router.get(
    "",
    response_model=list[SecurityFindingResponse],
)
def get_security_findings(
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> list[SecurityFindingResponse]:
    findings = list_security_findings(
        session
    )

    return [
        SecurityFindingResponse.model_validate(finding)
        for finding in findings
    ]


@router.get(
    "/{finding_id}",
    response_model=SecurityFindingResponse,
)
def get_security_finding_by_id_endpoint(
    finding_id: UUID,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> SecurityFindingResponse:
    finding = get_security_finding_by_id(
        session,
        finding_id,
    )

    if finding is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="security finding not found",
        )

    return SecurityFindingResponse.model_validate(
        finding
    )


@router.post(
    "/{finding_id}/resolve",
    response_model=SecurityFindingResponse,
)
def resolve_security_finding_endpoint(
    finding_id: UUID,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.OPERATOR)
    ),
) -> SecurityFindingResponse:
    try:
        finding = resolve_security_finding(
            session,
            finding_id,
            datetime.now(UTC),
        )
    except ValueError as error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="security finding not found",
        ) from error

    session.commit()
    session.refresh(finding)

    return SecurityFindingResponse.model_validate(
        finding
    )