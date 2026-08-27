import {
    NavLink,
    Outlet
} from "react-router-dom";

import { useAuth } from "./auth";

function navClassName(
    {
        isActive
    }: {
        isActive: boolean;
    }
): string {
    return isActive
        ? "nav-link nav-link--active"
        : "nav-link";
}

export function AppLayout() {
    const {
        user,
        logout
    } = useAuth();

    return (
        <div
            className="app-shell"
        >
            <aside
                className="sidebar"
            >
                <div
                    className="brand"
                >
                    <span
                        className="brand__mark"
                    >
                        N
                    </span>

                    <div>
                        <strong>
                            NexusOps
                        </strong>

                        <span>
                            Operator Console
                        </span>
                    </div>
                </div>

                <nav
                    className="navigation"
                    aria-label="Main navigation"
                >
                    <NavLink
                        to="/"
                        end
                        className={
                            navClassName
                        }
                    >
                        Overview
                    </NavLink>

                    <NavLink
                        to="/hosts"
                        className={
                            navClassName
                        }
                    >
                        Hosts
                    </NavLink>

                    <NavLink
                        to="/alerts"
                        className={
                            navClassName
                        }
                    >
                        Alerts
                    </NavLink>

                    <NavLink
                        to="/incidents"
                        className={
                            navClassName
                        }
                    >
                        Incidents
                    </NavLink>
                </nav>

                <div
                    className="sidebar__identity"
                >
                    <strong>
                        {
                            user?.username
                            ?? "Unknown user"
                        }
                    </strong>

                    <span>
                        {
                            user?.roles.length
                                ? user.roles.join(
                                    ", "
                                )
                                : "no roles"
                        }
                    </span>

                    <button
                        type="button"
                        className="button button--secondary button--full"
                        onClick={
                            () => {
                                void logout();
                            }
                        }
                    >
                        Sign out
                    </button>
                </div>
            </aside>

            <main
                className="main-content"
            >
                <Outlet />
            </main>
        </div>
    );
}