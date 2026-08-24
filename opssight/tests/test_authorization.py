import pytest

from opssight.auth.authorization import (
    AuthorizationError,
    authorize,
    has_required_role,
)
from opssight.auth.models import Principal, Role


def test_viewer_can_access_viewer_resource() -> None:
    principal = Principal(
        subject="user-1",
        role=Role.VIEWER,
    )

    assert has_required_role(
        principal,
        Role.VIEWER,
    )


def test_operator_can_access_viewer_resource() -> None:
    principal = Principal(
        subject="user-1",
        role=Role.OPERATOR,
    )

    assert has_required_role(
        principal,
        Role.VIEWER,
    )


def test_admin_can_access_operator_resource() -> None:
    principal = Principal(
        subject="user-1",
        role=Role.ADMIN,
    )

    assert has_required_role(
        principal,
        Role.OPERATOR,
    )


def test_viewer_cannot_access_operator_resource() -> None:
    principal = Principal(
        subject="user-1",
        role=Role.VIEWER,
    )

    assert not has_required_role(
        principal,
        Role.OPERATOR,
    )


def test_authorize_rejects_insufficient_role() -> None:
    principal = Principal(
        subject="user-1",
        role=Role.VIEWER,
    )

    with pytest.raises(
        AuthorizationError,
        match=(
            "role viewer does not satisfy "
            "required role operator"
        ),
    ):
        authorize(
            principal,
            Role.OPERATOR,
        )