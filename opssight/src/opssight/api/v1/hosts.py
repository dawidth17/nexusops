from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import require_role
from opssight.auth.models import Principal, Role
from opssight.repositories.host_repository import (
    create_host,
    get_host_by_id,
    list_hosts,
)
from opssight.schemas.host import (
    HostCreate,
    HostResponse,
)


router = APIRouter()


@router.get(
    "",
    response_model=list[HostResponse],
)
def get_hosts(
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> list[HostResponse]:
    hosts = list_hosts(
        session
    )

    return [
        HostResponse.model_validate(host)
        for host in hosts
    ]


@router.get(
    "/{host_id}",
    response_model=HostResponse,
)
def get_host_by_id_endpoint(
    host_id: UUID,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.VIEWER)
    ),
) -> HostResponse:
    host = get_host_by_id(
        session,
        host_id,
    )

    if host is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="host not found",
        )

    return HostResponse.model_validate(
        host
    )


@router.post(
    "",
    response_model=HostResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_host_endpoint(
    request: HostCreate,
    session: Session = Depends(
        get_session
    ),
    _principal: Principal = Depends(
        require_role(Role.ADMIN)
    ),
) -> HostResponse:
    try:
        host = create_host(
            session,
            name=request.name,
            address=request.address,
        )

        session.commit()
        session.refresh(host)
    except IntegrityError as error:
        session.rollback()

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="host name already exists",
        ) from error

    return HostResponse.model_validate(
        host
    )