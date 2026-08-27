import {
    Navigate,
    Outlet,
    Route,
    Routes
} from "react-router-dom";

import { useAuth } from "./auth";

import {
    AppLayout
} from "./layout";

import {
    AlertDetailPage,
    AlertsPage
} from "./pages/AlertsPage";

import {
    HostsPage
} from "./pages/HostsPage";

import {
    IncidentDetailPage,
    IncidentsPage
} from "./pages/IncidentsPage";

import {
    LoginPage
} from "./pages/LoginPage";

import {
    OverviewPage
} from "./pages/OverviewPage";

import {
    StatePanel
} from "./ui";

function ProtectedRoute() {
    const {
        status
    } = useAuth();

    if (status === "loading") {
        return (
            <StatePanel
                title="Loading NexusOps"
                message="Restoring the authenticated Console session."
            />
        );
    }

    if (
        status
        !== "authenticated"
    ) {
        return (
            <Navigate
                to="/login"
                replace
            />
        );
    }

    return <Outlet />;
}

function NotFoundPage() {
    return (
        <StatePanel
            title="Page not found"
            message="The requested NexusOps Console page does not exist."
        />
    );
}

export function App() {
    return (
        <Routes>
            <Route
                path="/login"
                element={
                    <LoginPage />
                }
            />

            <Route
                element={
                    <ProtectedRoute />
                }
            >
                <Route
                    element={
                        <AppLayout />
                    }
                >
                    <Route
                        index
                        element={
                            <OverviewPage />
                        }
                    />

                    <Route
                        path="hosts"
                        element={
                            <HostsPage />
                        }
                    />

                    <Route
                        path="alerts"
                        element={
                            <AlertsPage />
                        }
                    />

                    <Route
                        path="alerts/:alertId"
                        element={
                            <AlertDetailPage />
                        }
                    />

                    <Route
                        path="incidents"
                        element={
                            <IncidentsPage />
                        }
                    />

                    <Route
                        path="incidents/:incidentId"
                        element={
                            <IncidentDetailPage />
                        }
                    />

                    <Route
                        path="*"
                        element={
                            <NotFoundPage />
                        }
                    />
                </Route>
            </Route>
        </Routes>
    );
}