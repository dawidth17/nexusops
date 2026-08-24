from opssight.auth.models import Principal, Role


class AuthorizationError(Exception):
    pass


def has_required_role(
    principal: Principal,
    required_role: Role,
) -> bool:
    return principal.role >= required_role


def authorize(
    principal: Principal,
    required_role: Role,
) -> None:
    if not has_required_role(
        principal,
        required_role,
    ):
        raise AuthorizationError(
            f"role {principal.role.name.lower()} "
            f"does not satisfy required role "
            f"{required_role.name.lower()}"
        )