import {
    useEffect,
    useState
} from "react";

import {
    Link
} from "react-router-dom";

import {
    type Alert,
    type Check,
    type Host,
    type Incident,
    listAlerts,
    listChecks,
    listHosts,
    listIncidents
} from "../api";

import { useAuth } from "../auth";

import {
    formatDate,
    getHostOperationalStatus
} from "../domain";

import {
    PageHeader,
    StatCard,
    StatePanel,
    StatusBadge
} from "../ui";

interface OverviewData {
    hosts: Host[];
    checks: Check[];
    alerts: Alert[];
    incidents: Incident[];
}

export function OverviewPage() {
    const {
        accessToken
    } = useAuth();

    const [
        data,
        setData
    ] = useState<OverviewData | null>(
        null
    );

    const [
        error,
        setError
    ] = useState<string | null>(
        null
    );

    useEffect(
        () => {
            if (accessToken === null) {
                return;
            }

            const token =
                accessToken;

            let cancelled = false;

            async function load():
                Promise<void> {
                try {
                    const [
                        hosts,
                        checks,
                        alerts,
                        incidents
                    ] =
                        await Promise.all([
                            listHosts(
                                token
                            ),
                            listChecks(
                                token
                            ),
                            listAlerts(
                                token
                            ),
                            listIncidents(
                                token
                            )
                        ]);

                    if (!cancelled) {
                        setData({
                            hosts,
                            checks,
                            alerts,
                            incidents
                        });

                        setError(null);
                    }
                } catch {
                    if (!cancelled) {
                        setError(
                            "Platform state could not be loaded."
                        );
                    }
                }
            }

            void load();

            return () => {
                cancelled = true;
            };
        },
        [
            accessToken
        ]
    );

    if (error !== null) {
        return (
            <StatePanel
                title="Unable to load Overview"
                message={error}
            />
        );
    }

    if (data === null) {
        return (
            <StatePanel
                title="Loading Overview"
                message="Collecting current NexusOps operational state."
            />
        );
    }

    const degradedHosts =
        data.hosts.filter(
            host =>
                getHostOperationalStatus(
                    host,
                    data.checks
                )
                === "degraded"
        ).length;

    const openAlerts =
        data.alerts.filter(
            alert =>
                alert.status === "open"
        ).length;

    const activeIncidents =
        data.incidents.filter(
            incident =>
                incident.status
                !== "CLOSED"
        ).length;

    const recentAlerts =
        [...data.alerts]
            .sort(
                (left, right) =>
                    Date.parse(
                        right.opened_at
                    )
                    - Date.parse(
                        left.opened_at
                    )
            )
            .slice(0, 5);

    const recentIncidents =
        [...data.incidents]
            .sort(
                (left, right) =>
                    Date.parse(
                        right.createdAt
                    )
                    - Date.parse(
                        left.createdAt
                    )
            )
            .slice(0, 5);

    return (
        <>
            <PageHeader
                title="Overview"
                description="Current monitoring and incident state across NexusOps."
            />

            <section
                className="stat-grid"
            >
                <StatCard
                    label="Monitored hosts"
                    value={
                        data.hosts.length
                    }
                    description={`${degradedHosts} degraded`}
                />

                <StatCard
                    label="Checks"
                    value={
                        data.checks.length
                    }
                    description="Configured OpsSight checks"
                />

                <StatCard
                    label="Open alerts"
                    value={openAlerts}
                    description="Active monitoring alerts"
                />

                <StatCard
                    label="Active incidents"
                    value={activeIncidents}
                    description="Not yet closed in ServiceCore"
                />
            </section>

            <section
                className="overview-grid"
            >
                <article
                    className="panel"
                >
                    <div
                        className="panel__header"
                    >
                        <div>
                            <h2>
                                Recent alerts
                            </h2>

                            <p>
                                Latest OpsSight
                                alert lifecycle state.
                            </p>
                        </div>

                        <Link
                            to="/alerts"
                        >
                            View all
                        </Link>
                    </div>

                    {
                        recentAlerts.length === 0
                            ? (
                                <p
                                    className="empty-copy"
                                >
                                    No alerts have been recorded.
                                </p>
                            )
                            : (
                                <div
                                    className="activity-list"
                                >
                                    {
                                        recentAlerts.map(
                                            alert => (
                                                <Link
                                                    key={
                                                        alert.id
                                                    }
                                                    className="activity-item"
                                                    to={
                                                        `/alerts/${alert.id}`
                                                    }
                                                >
                                                    <div>
                                                        <strong>
                                                            {
                                                                alert.message
                                                            }
                                                        </strong>

                                                        <span>
                                                            {
                                                                formatDate(
                                                                    alert.opened_at
                                                                )
                                                            }
                                                        </span>
                                                    </div>

                                                    <StatusBadge
                                                        value={
                                                            alert.status
                                                        }
                                                    />
                                                </Link>
                                            )
                                        )
                                    }
                                </div>
                            )
                    }
                </article>

                <article
                    className="panel"
                >
                    <div
                        className="panel__header"
                    >
                        <div>
                            <h2>
                                Recent incidents
                            </h2>

                            <p>
                                Latest ServiceCore
                                operational tickets.
                            </p>
                        </div>

                        <Link
                            to="/incidents"
                        >
                            View all
                        </Link>
                    </div>

                    {
                        recentIncidents.length === 0
                            ? (
                                <p
                                    className="empty-copy"
                                >
                                    No incidents have been recorded.
                                </p>
                            )
                            : (
                                <div
                                    className="activity-list"
                                >
                                    {
                                        recentIncidents.map(
                                            incident => (
                                                <Link
                                                    key={
                                                        incident.id
                                                    }
                                                    className="activity-item"
                                                    to={
                                                        `/incidents/${incident.id}`
                                                    }
                                                >
                                                    <div>
                                                        <strong>
                                                            {
                                                                incident.title
                                                            }
                                                        </strong>

                                                        <span>
                                                            {
                                                                incident.priority
                                                            }
                                                            {" · "}
                                                            {
                                                                formatDate(
                                                                    incident.createdAt
                                                                )
                                                            }
                                                        </span>
                                                    </div>

                                                    <StatusBadge
                                                        value={
                                                            incident.status
                                                        }
                                                    />
                                                </Link>
                                            )
                                        )
                                    }
                                </div>
                            )
                    }
                </article>
            </section>
        </>
    );
}