from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from opssight.auth.dependencies import (
    get_current_principal,
    require_role,
)
from opssight.auth.models import Principal, Role


def create_test_app(
    required_role: Role,
) -> FastAPI:
    app = FastAPI()

    @app.get("/protected")
    def protected_endpoint(
        principal: Principal = Depends(
            require_role(required_role)
        ),
    ) -> dict[str, str]:
        return {
            "subject": principal.subject,
            "role": principal.role.name.lower(),
        }

    return app


def test_missing_authentication_returns_401() -> None:
    app = create_test_app(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/protected"
    )

    assert response.status_code == 401
    assert response.json() == {
        "detail": (
            "authentication provider "
            "is not configured"
        )
    }


def test_viewer_can_access_viewer_endpoint() -> None:
    app = create_test_app(
        Role.VIEWER
    )

    def override_principal() -> Principal:
        return Principal(
            subject="viewer-user",
            role=Role.VIEWER,
        )

    app.dependency_overrides[
        get_current_principal
    ] = override_principal

    client = TestClient(app)

    response = client.get(
        "/protected"
    )

    assert response.status_code == 200
    assert response.json() == {
        "subject": "viewer-user",
        "role": "viewer",
    }


def test_viewer_cannot_access_operator_endpoint() -> None:
    app = create_test_app(
        Role.OPERATOR
    )

    def override_principal() -> Principal:
        return Principal(
            subject="viewer-user",
            role=Role.VIEWER,
        )

    app.dependency_overrides[
        get_current_principal
    ] = override_principal

    client = TestClient(app)

    response = client.get(
        "/protected"
    )

    assert response.status_code == 403
    assert response.json() == {
        "detail": (
            "role viewer does not satisfy "
            "required role operator"
        )
    }


def test_operator_can_access_operator_endpoint() -> None:
    app = create_test_app(
        Role.OPERATOR
    )

    def override_principal() -> Principal:
        return Principal(
            subject="operator-user",
            role=Role.OPERATOR,
        )

    app.dependency_overrides[
        get_current_principal
    ] = override_principal

    client = TestClient(app)

    response = client.get(
        "/protected"
    )

    assert response.status_code == 200
    assert response.json() == {
        "subject": "operator-user",
        "role": "operator",
    }


def test_admin_can_access_operator_endpoint() -> None:
    app = create_test_app(
        Role.OPERATOR
    )

    def override_principal() -> Principal:
        return Principal(
            subject="admin-user",
            role=Role.ADMIN,
        )

    app.dependency_overrides[
        get_current_principal
    ] = override_principal

    client = TestClient(app)

    response = client.get(
        "/protected"
    )

    assert response.status_code == 200
    assert response.json() == {
        "subject": "admin-user",
        "role": "admin",
    }