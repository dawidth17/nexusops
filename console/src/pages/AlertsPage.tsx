import {
    useEffect,
    useMemo,
    useState
} from "react";

import {
    Link,
    useParams
} from "react-router-dom";

import {
    type Alert,
    type Incident,
    getAlert,
    listAlerts,
    listIncidents
} from "../api";

import { useAuth } from "../auth";

import {
    formatDate,
    shortIdentifier
} from "../domain";

import {
    DetailItem,
    PageHeader,
    StatePanel,
    StatusBadge
} from "../ui";

interface AlertsData {
    alerts: Alert[];
    incidents: Incident[];
}

export function AlertsPage() {
    const {
        accessToken
    } = useAuth();

    const [
        data,
        setData
    ] = useState<AlertsData | null>(
        null
    );

    const [
        query,
        setQuery
    ] = useState("");

    const [
        statusFilter,
        setStatusFilter
    ] = useState("all");

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
                        alerts,
                        incidents
                    ] =
                        await Promise.all([
                            listAlerts(
                                token
                            ),
                            listIncidents(
                                token
                            )
                        ]);

                    if (!cancelled) {
                        setData({
                            alerts,
                            incidents
                        });

                        setError(null);
                    }
                } catch {
                    if (!cancelled) {
                        setError(
                            "Alert data could not be loaded."
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

    const filteredAlerts =
        useMemo(
            () => {
                if (data === null) {
                    return [];
                }

                const normalized =
                    query
                        .trim()
                        .toLowerCase();

                return [
                    ...data.alerts
                ]
                    .filter(
                        alert =>
                            statusFilter
                                === "all"
                            || alert.status
                                === statusFilter
                    )
                    .filter(
                        alert =>
                            !normalized
                            || alert.message
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            || alert
                                .correlation_id
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                    )
                    .sort(
                        (left, right) =>
                            Date.parse(
                                right.opened_at
                            )
                            - Date.parse(
                                left.opened_at
                            )
                    );
            },
            [
                data,
                query,
                statusFilter
            ]
        );

    if (error !== null) {
        return (
            <StatePanel
                title="Unable to load Alerts"
                message={error}
            />
        );
    }

    if (data === null) {
        return (
            <StatePanel
                title="Loading Alerts"
                message="Reading OpsSight alert lifecycle state."
            />
        );
    }

    return (
        <>
            <PageHeader
                title="Alerts"
                description="OpsSight alerts with cross-platform incident context."
            />

            <div
                className="toolbar toolbar--split"
            >
                <label>
                    <span>
                        Search
                    </span>

                    <input
                        type="search"
                        value={query}
                        placeholder="Message or correlation ID"
                        onChange={
                            event =>
                                setQuery(
                                    event.target.value
                                )
                        }
                    />
                </label>

                <label>
                    <span>
                        Status
                    </span>

                    <select
                        value={
                            statusFilter
                        }
                        onChange={
                            event =>
                                setStatusFilter(
                                    event.target.value
                                )
                        }
                    >
                        <option
                            value="all"
                        >
                            All
                        </option>

                        <option
                            value="open"
                        >
                            Open
                        </option>

                        <option
                            value="recovered"
                        >
                            Recovered
                        </option>
                    </select>
                </label>
            </div>

            <section
                className="panel table-panel"
            >
                <div
                    className="table-wrapper"
                >
                    <table>
                        <thead>
                            <tr>
                                <th>
                                    Alert
                                </th>

                                <th>
                                    Status
                                </th>

                                <th>
                                    Correlation
                                </th>

                                <th>
                                    Opened
                                </th>

                                <th>
                                    Incident
                                </th>
                            </tr>
                        </thead>

                        <tbody>
                            {
                                filteredAlerts.length
                                === 0
                                    ? (
                                        <tr>
                                            <td
                                                colSpan={
                                                    5
                                                }
                                                className="empty-cell"
                                            >
                                                No alerts match the current filters.
                                            </td>
                                        </tr>
                                    )
                                    : (
                                        filteredAlerts.map(
                                            alert => {
                                                const incident =
                                                    data.incidents.find(
                                                        item =>
                                                            item.sourceAlertId
                                                            === alert.id
                                                    );

                                                return (
                                                    <tr
                                                        key={
                                                            alert.id
                                                        }
                                                    >
                                                        <td>
                                                            <Link
                                                                className="table-link"
                                                                to={
                                                                    `/alerts/${alert.id}`
                                                                }
                                                            >
                                                                {
                                                                    alert.message
                                                                }
                                                            </Link>
                                                        </td>

                                                        <td>
                                                            <StatusBadge
                                                                value={
                                                                    alert.status
                                                                }
                                                            />
                                                        </td>

                                                        <td>
                                                            <code>
                                                                {
                                                                    shortIdentifier(
                                                                        alert.correlation_id
                                                                    )
                                                                }
                                                            </code>
                                                        </td>

                                                        <td>
                                                            {
                                                                formatDate(
                                                                    alert.opened_at
                                                                )
                                                            }
                                                        </td>

                                                        <td>
                                                            {
                                                                incident
                                                                    ? (
                                                                        <Link
                                                                            to={
                                                                                `/incidents/${incident.id}`
                                                                            }
                                                                        >
                                                                            {
                                                                                incident.title
                                                                            }
                                                                        </Link>
                                                                    )
                                                                    : (
                                                                        <span
                                                                            className="muted"
                                                                        >
                                                                            —
                                                                        </span>
                                                                    )
                                                            }
                                                        </td>
                                                    </tr>
                                                );
                                            }
                                        )
                                    )
                            }
                        </tbody>
                    </table>
                </div>
            </section>
        </>
    );
}

export function AlertDetailPage() {
    const {
        alertId
    } = useParams();

    const {
        accessToken
    } = useAuth();

    const [
        alert,
        setAlert
    ] = useState<Alert | null>(
        null
    );

    const [
        linkedIncident,
        setLinkedIncident
    ] = useState<Incident | null>(
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
            if (
                accessToken === null
                || alertId === undefined
            ) {
                return;
            }

            const token =
                accessToken;

            const resolvedAlertId =
                alertId;

            let cancelled = false;

            async function load():
                Promise<void> {
                try {
                    const [
                        loadedAlert,
                        incidents
                    ] =
                        await Promise.all([
                            getAlert(
                                token,
                                resolvedAlertId
                            ),
                            listIncidents(
                                token
                            )
                        ]);

                    if (!cancelled) {
                        setAlert(
                            loadedAlert
                        );

                        setLinkedIncident(
                            incidents.find(
                                incident =>
                                    incident
                                        .sourceAlertId
                                    === loadedAlert.id
                            )
                            ?? null
                        );

                        setError(null);
                    }
                } catch {
                    if (!cancelled) {
                        setError(
                            "The alert could not be loaded."
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
            accessToken,
            alertId
        ]
    );

    if (error !== null) {
        return (
            <StatePanel
                title="Unable to load Alert"
                message={error}
            />
        );
    }

    if (alert === null) {
        return (
            <StatePanel
                title="Loading Alert"
                message="Reading alert and linked incident context."
            />
        );
    }

    return (
        <>
            <Link
                className="back-link"
                to="/alerts"
            >
                ← Back to alerts
            </Link>

            <PageHeader
                title="Alert details"
                description={alert.message}
                actions={
                    <StatusBadge
                        value={
                            alert.status
                        }
                    />
                }
            />

            <section
                className="panel"
            >
                <dl
                    className="detail-grid"
                >
                    <DetailItem
                        label="Alert ID"
                    >
                        <code>
                            {alert.id}
                        </code>
                    </DetailItem>

                    <DetailItem
                        label="Alert rule ID"
                    >
                        <code>
                            {
                                alert.alert_rule_id
                            }
                        </code>
                    </DetailItem>

                    <DetailItem
                        label="Correlation ID"
                    >
                        <code>
                            {
                                alert.correlation_id
                            }
                        </code>
                    </DetailItem>

                    <DetailItem
                        label="Opened"
                    >
                        {
                            formatDate(
                                alert.opened_at
                            )
                        }
                    </DetailItem>

                    <DetailItem
                        label="Recovered"
                    >
                        {
                            formatDate(
                                alert.recovered_at
                            )
                        }
                    </DetailItem>
                </dl>
            </section>

            <section
                className="panel linked-panel"
            >
                <h2>
                    ServiceCore incident
                </h2>

                {
                    linkedIncident === null
                        ? (
                            <p
                                className="empty-copy"
                            >
                                No ServiceCore incident is currently linked to this alert.
                            </p>
                        )
                        : (
                            <div
                                className="linked-resource"
                            >
                                <div>
                                    <strong>
                                        {
                                            linkedIncident.title
                                        }
                                    </strong>

                                    <span>
                                        {
                                            linkedIncident.priority
                                        }
                                        {" · "}
                                        {
                                            linkedIncident.status
                                        }
                                    </span>
                                </div>

                                <Link
                                    className="button button--secondary"
                                    to={
                                        `/incidents/${linkedIncident.id}`
                                    }
                                >
                                    View incident
                                </Link>
                            </div>
                        )
                }
            </section>
        </>
    );
}