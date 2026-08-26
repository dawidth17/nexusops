from typing import Any, Protocol

import jwt
from jwt import PyJWKClient
from jwt.exceptions import (
    InvalidTokenError,
    PyJWKClientError,
)

from opssight.auth.models import Principal, Role


class OidcAuthenticationError(Exception):
    pass


class OidcAuthorizationError(Exception):
    pass


class SigningKey(Protocol):
    key: Any


class SigningKeyProvider(Protocol):
    def get_signing_key_from_jwt(
        self,
        token: str,
    ) -> SigningKey:
        ...


def _collect_role_values(
    raw_roles: object,
) -> set[str]:
    if not isinstance(
        raw_roles,
        list,
    ):
        return set()

    roles: set[str] = set()

    for raw_role in raw_roles:
        if not isinstance(
            raw_role,
            str,
        ):
            continue

        role = raw_role.strip().lower()

        if role:
            roles.add(
                role
            )

    return roles


def _extract_token_roles(
    claims: dict[str, Any],
    client_id: str,
) -> set[str]:
    roles: set[str] = set()

    realm_access = claims.get(
        "realm_access"
    )

    if isinstance(
        realm_access,
        dict,
    ):
        roles.update(
            _collect_role_values(
                realm_access.get(
                    "roles"
                )
            )
        )

    resource_access = claims.get(
        "resource_access"
    )

    if isinstance(
        resource_access,
        dict,
    ):
        client_access = (
            resource_access.get(
                client_id
            )
        )

        if isinstance(
            client_access,
            dict,
        ):
            roles.update(
                _collect_role_values(
                    client_access.get(
                        "roles"
                    )
                )
            )

    return roles


def _resolve_opssight_role(
    roles: set[str],
) -> Role:
    if "admin" in roles:
        return Role.ADMIN

    if roles.intersection(
        {
            "operator",
            "technician",
            "manager",
        }
    ):
        return Role.OPERATOR

    if roles.intersection(
        {
            "viewer",
            "employee",
        }
    ):
        return Role.VIEWER

    raise OidcAuthorizationError(
        "authenticated user has no OpsSight role"
    )


class OidcTokenVerifier:
    def __init__(
        self,
        issuer: str,
        jwks_url: str,
        client_id: str,
        signing_key_provider: (
            SigningKeyProvider | None
        ) = None,
    ) -> None:
        if not issuer.strip():
            raise ValueError(
                "OIDC issuer must not be empty"
            )

        if not jwks_url.strip():
            raise ValueError(
                "OIDC JWKS URL must not be empty"
            )

        if not client_id.strip():
            raise ValueError(
                "OIDC client id must not be empty"
            )

        self._issuer = issuer.rstrip(
            "/"
        )

        self._client_id = (
            client_id.strip()
        )

        self._signing_key_provider = (
            signing_key_provider
            if signing_key_provider
            is not None
            else PyJWKClient(
                jwks_url
            )
        )

    def verify(
        self,
        token: str,
    ) -> Principal:
        if not token.strip():
            raise OidcAuthenticationError(
                "access token must not be empty"
            )

        try:
            signing_key = (
                self._signing_key_provider
                .get_signing_key_from_jwt(
                    token
                )
            )

            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=[
                    "RS256",
                ],
                issuer=self._issuer,
                options={
                    "require": [
                        "exp",
                        "iat",
                        "iss",
                        "sub",
                    ],
                    "verify_aud": False,
                },
            )

        except (
            InvalidTokenError,
            PyJWKClientError,
            ValueError,
        ) as error:
            raise OidcAuthenticationError(
                "access token is invalid"
            ) from error

        subject = claims.get(
            "sub"
        )

        if (
            not isinstance(
                subject,
                str,
            )
            or not subject.strip()
        ):
            raise OidcAuthenticationError(
                "access token subject is invalid"
            )

        roles = _extract_token_roles(
            claims,
            self._client_id,
        )

        role = _resolve_opssight_role(
            roles
        )

        return Principal(
            subject=subject.strip(),
            role=role,
        )