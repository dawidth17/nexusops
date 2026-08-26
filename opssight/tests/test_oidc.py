from datetime import (
    datetime,
    timedelta,
    timezone,
)
from typing import Any

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import (
    rsa,
)

from opssight.auth.models import Role
from opssight.auth.oidc import (
    OidcAuthenticationError,
    OidcAuthorizationError,
    OidcTokenVerifier,
)

ISSUER = (
    "http://127.0.0.1:8081/"
    "realms/nexusops"
)

JWKS_URL = (
    f"{ISSUER}/protocol/"
    "openid-connect/certs"
)

CLIENT_ID = "opssight"


class StaticSigningKey:
    def __init__(
        self,
        key: Any,
    ) -> None:
        self.key = key


class StaticSigningKeyProvider:
    def __init__(
        self,
        key: Any,
    ) -> None:
        self._key = key

    def get_signing_key_from_jwt(
        self,
        token: str,
    ) -> StaticSigningKey:
        del token

        return StaticSigningKey(
            self._key
        )


def create_private_key() -> Any:
    return rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )


def create_token(
    private_key: Any,
    *,
    issuer: str = ISSUER,
    subject: str = "user-123",
    roles: list[str] | None = None,
    expires_in: timedelta = (
        timedelta(minutes=5)
    ),
) -> str:
    now = datetime.now(
        timezone.utc
    )

    return jwt.encode(
        {
            "iss": issuer,
            "sub": subject,
            "iat": now,
            "exp": now + expires_in,
            "realm_access": {
                "roles": (
                    roles
                    if roles is not None
                    else ["viewer"]
                )
            },
        },
        private_key,
        algorithm="RS256",
        headers={
            "kid": "test-key",
        },
    )


def create_verifier(
    private_key: Any,
) -> OidcTokenVerifier:
    return OidcTokenVerifier(
        issuer=ISSUER,
        jwks_url=JWKS_URL,
        client_id=CLIENT_ID,
        signing_key_provider=(
            StaticSigningKeyProvider(
                private_key.public_key()
            )
        ),
    )


def test_valid_viewer_token_maps_to_viewer(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        roles=[
            "viewer",
        ],
    )

    principal = create_verifier(
        private_key
    ).verify(
        token
    )

    assert principal.subject == "user-123"
    assert principal.role == Role.VIEWER


def test_employee_maps_to_viewer(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        roles=[
            "employee",
        ],
    )

    principal = create_verifier(
        private_key
    ).verify(
        token
    )

    assert principal.role == Role.VIEWER


def test_technician_maps_to_operator(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        roles=[
            "employee",
            "viewer",
            "operator",
            "technician",
        ],
    )

    principal = create_verifier(
        private_key
    ).verify(
        token
    )

    assert principal.role == Role.OPERATOR


def test_manager_maps_to_operator(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        roles=[
            "manager",
        ],
    )

    principal = create_verifier(
        private_key
    ).verify(
        token
    )

    assert principal.role == Role.OPERATOR


def test_admin_maps_to_admin(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        roles=[
            "admin",
        ],
    )

    principal = create_verifier(
        private_key
    ).verify(
        token
    )

    assert principal.role == Role.ADMIN


def test_expired_token_is_rejected(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        expires_in=timedelta(
            minutes=-5
        ),
    )

    with pytest.raises(
        OidcAuthenticationError
    ):
        create_verifier(
            private_key
        ).verify(
            token
        )


def test_invalid_issuer_is_rejected(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        issuer=(
            "http://attacker.invalid/"
            "realms/nexusops"
        ),
    )

    with pytest.raises(
        OidcAuthenticationError
    ):
        create_verifier(
            private_key
        ).verify(
            token
        )


def test_invalid_signature_is_rejected(
) -> None:
    trusted_key = create_private_key()
    attacker_key = create_private_key()

    token = create_token(
        attacker_key
    )

    with pytest.raises(
        OidcAuthenticationError
    ):
        create_verifier(
            trusted_key
        ).verify(
            token
        )


def test_token_without_nexusops_role_is_rejected(
) -> None:
    private_key = create_private_key()

    token = create_token(
        private_key,
        roles=[
            "offline_access",
        ],
    )

    with pytest.raises(
        OidcAuthorizationError
    ):
        create_verifier(
            private_key
        ).verify(
            token
        )