from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import require_role
from opssight.auth.models import Principal, Role
from opssight.repositories.runbook_repository import (
    create_runbook,
    get_runbook_by_id,
    list_runbooks,
)
from opssight.schemas.runbook import (
    RunbookCreate,
    RunbookResponse,
)


router = APIRouter()


@router.get(
    "",
    response_model=list[RunbookResponse],
)
def get_runbooks(
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> list[RunbookResponse]:
    runbooks = list_runbooks(
        session
    )

    return [
        RunbookResponse.model_validate(runbook)
        for runbook in runbooks
    ]


@router.get(
    "/{runbook_id}",
    response_model=RunbookResponse,
)
def get_runbook_by_id_endpoint(
    runbook_id: UUID,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> RunbookResponse:
    runbook = get_runbook_by_id(
        session,
        runbook_id,
    )

    if runbook is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="runbook not found",
        )

    return RunbookResponse.model_validate(
        runbook
    )


@router.post(
    "",
    response_model=RunbookResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_runbook_endpoint(
    request: RunbookCreate,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.ADMIN)
    ),
) -> RunbookResponse:
    try:
        runbook = create_runbook(
            session,
            name=request.name,
            description=request.description,
            instructions=request.instructions,
        )

        session.commit()
        session.refresh(runbook)
    except IntegrityError as error:
        session.rollback()

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="runbook name already exists",
        ) from error

    return RunbookResponse.model_validate(
        runbook
    )