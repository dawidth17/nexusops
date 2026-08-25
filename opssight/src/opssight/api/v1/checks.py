from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import require_role
from opssight.auth.models import Principal, Role
from opssight.repositories.check_repository import (
    create_check,
    get_check_by_id,
    list_checks,
    list_checks_by_host,
)
from opssight.repositories.host_repository import get_host_by_id
from opssight.schemas.check import (
    CheckCreate,
    CheckResponse,
)

router = APIRouter()


@router.get(
    "",
    response_model=list[CheckResponse],
)
def get_checks(
    host_id: UUID | None = Query(
        default=None,
    ),
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> list[CheckResponse]:
    if host_id is None:
        checks = list_checks(
            session
        )
    else:
        checks = list_checks_by_host(
            session,
            host_id,
        )

    return [
        CheckResponse.model_validate(check)
        for check in checks
    ]


@router.get(
    "/{check_id}",
    response_model=CheckResponse,
)
def get_check_by_id_endpoint(
    check_id: UUID,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> CheckResponse:
    check = get_check_by_id(
        session,
        check_id,
    )

    if check is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="check not found",
        )

    return CheckResponse.model_validate(
        check
    )


@router.post(
    "",
    response_model=CheckResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_check_endpoint(
    request: CheckCreate,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.ADMIN)
    ),
) -> CheckResponse:
    host = get_host_by_id(
        session,
        request.host_id,
    )

    if host is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="host not found",
        )

    try:
        check = create_check(
            session,
            host_id=request.host_id,
            name=request.name,
            check_type=request.check_type,
            target=request.target,
            interval_seconds=request.interval_seconds,
            timeout_seconds=request.timeout_seconds,
        )

        session.commit()
        session.refresh(check)
    except IntegrityError as error:
        session.rollback()

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="check name already exists for host",
        ) from error

    return CheckResponse.model_validate(
        check
    )