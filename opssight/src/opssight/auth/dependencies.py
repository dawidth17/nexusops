from collections.abc import Callable

from fastapi import Depends, HTTPException, status

from opssight.auth.authorization import (
    AuthorizationError,
    authorize,
)
from opssight.auth.models import Principal, Role


def get_current_principal() -> Principal:
    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="authentication provider is not configured",
    )


def require_role(
    required_role: Role,
) -> Callable[..., Principal]:
    def dependency(
        principal: Principal = Depends(
            get_current_principal
        ),
    ) -> Principal:
        try:
            authorize(
                principal,
                required_role,
            )
        except AuthorizationError as error:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=str(error),
            ) from error

        return principal

    return dependency