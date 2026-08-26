from collections.abc import Callable
from functools import lru_cache

from fastapi import (
    Depends,
    HTTPException,
    status,
)
from fastapi.security import (
    HTTPAuthorizationCredentials,
    HTTPBearer,
)

from opssight.auth.authorization import (
    AuthorizationError,
    authorize,
)
from opssight.auth.models import Principal, Role
from opssight.auth.oidc import (
    OidcAuthenticationError,
    OidcAuthorizationError,
    OidcTokenVerifier,
)
from opssight.config import settings

_bearer_scheme = HTTPBearer(
    auto_error=False
)


@lru_cache(maxsize=1)
def get_oidc_token_verifier(
) -> OidcTokenVerifier:
    return OidcTokenVerifier(
        issuer=settings.oidc_issuer,
        jwks_url=settings.oidc_jwks_url,
        client_id=settings.oidc_client_id,
    )


def _authentication_error(
    detail: str,
) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={
            "WWW-Authenticate": "Bearer",
        },
    )


def get_current_principal(
    credentials: (
        HTTPAuthorizationCredentials
        | None
    ) = Depends(
        _bearer_scheme
    ),
) -> Principal:
    if not settings.oidc_enabled:
        raise _authentication_error(
            "authentication provider "
            "is not configured"
        )

    if credentials is None:
        raise _authentication_error(
            "authentication credentials "
            "are required"
        )

    if (
        credentials.scheme.lower()
        != "bearer"
    ):
        raise _authentication_error(
            "authentication scheme "
            "must be Bearer"
        )

    try:
        return (
            get_oidc_token_verifier()
            .verify(
                credentials.credentials
            )
        )

    except OidcAuthenticationError as error:
        raise _authentication_error(
            str(error)
        ) from error

    except OidcAuthorizationError as error:
        raise HTTPException(
            status_code=(
                status.HTTP_403_FORBIDDEN
            ),
            detail=str(error),
        ) from error


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
                status_code=(
                    status.HTTP_403_FORBIDDEN
                ),
                detail=str(error),
            ) from error

        return principal

    return dependency