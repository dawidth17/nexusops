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
    type IncidentAction,
    getAlert,
    getIncident,
    listIncidents,
    transitionIncident
} from "../api";

import { useAuth } from "../auth";

import {
    canOperateIncidents,
    formatDate,
    getIncidentTransitions,
    shortIdentifier
} from "../domain";

import {
    DetailItem,
    PageHeader,
    StatePanel,
    StatusBadge
} from "../ui";

export function IncidentsPage() {
    const {
        accessToken
    } = useAuth();

    const [
        incidents,
        setIncidents
    ] = useState<Incident[] | null>(
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
        sourceFilter,
        setSourceFilter
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
                    const loaded =
                        await listIncidents(
                            token
                        );

                    if (!cancelled) {
                        setIncidents(
                            loaded
                        );

                        setError(null);
                    }
                } catch {
                    if (!cancelled) {
                        setError(
                            "Incident data could not be loaded."
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

    const filteredIncidents =
        useMemo(
            () => {
                if (
                    incidents === null
                ) {
                    return [];
                }

                const normalized =
                    query
                        .trim()
                        .toLowerCase();

                return incidents
                    .filter(
                        incident =>
                            statusFilter
                                === "all"
                            || incident.status
                                === statusFilter
                    )
                    .filter(
                        incident =>
                            sourceFilter
                                === "all"
                            || incident.source
                                === sourceFilter
                    )
                    .filter(
                        incident =>
                            !normalized
                            || incident.title
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            || (
                                incident
                                    .correlationId
                                ?.toLowerCase()
                                .includes(
                                    normalized
                                )
                                ?? false
                            )
                    );
            },
            [
                incidents,
                query,
                statusFilter,
                sourceFilter
            ]
        );

    if (error !== null) {
        return (
            <StatePanel
                title="Unable to load Incidents"
                message={error}
            />
        );
    }

    if (incidents === null) {
        return (
            <StatePanel
                title="Loading Incidents"
                message="Reading ServiceCore incident state."
            />
        );
    }

    return (
        <>
            <PageHeader
                title="Incidents"
                description="ServiceCore incidents and their monitoring correlation context."
            />

            <div
                className="toolbar toolbar--triple"
            >
                <label>
                    <span>
                        Search
                    </span>

                    <input
                        type="search"
                        value={query}
                        placeholder="Title or correlation ID"
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
                        <option value="all">
                            All
                        </option>

                        <option value="OPEN">
                            Open
                        </option>

                        <option value="IN_PROGRESS">
                            In progress
                        </option>

                        <option value="RESOLVED">
                            Resolved
                        </option>

                        <option value="CLOSED">
                            Closed
                        </option>
                    </select>
                </label>

                <label>
                    <span>
                        Source
                    </span>

                    <select
                        value={
                            sourceFilter
                        }
                        onChange={
                            event =>
                                setSourceFilter(
                                    event.target.value
                                )
                        }
                    >
                        <option value="all">
                            All
                        </option>

                        <option value="MANUAL">
                            Manual
                        </option>

                        <option value="MONITORING">
                            Monitoring
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
                                    Incident
                                </th>

                                <th>
                                    Priority
                                </th>

                                <th>
                                    Status
                                </th>

                                <th>
                                    Source
                                </th>

                                <th>
                                    Correlation
                                </th>

                                <th>
                                    Created
                                </th>
                            </tr>
                        </thead>

                        <tbody>
                            {
                                filteredIncidents.length
                                === 0
                                    ? (
                                        <tr>
                                            <td
                                                colSpan={
                                                    6
                                                }
                                                className="empty-cell"
                                            >
                                                No incidents match the current filters.
                                            </td>
                                        </tr>
                                    )
                                    : (
                                        filteredIncidents.map(
                                            incident => (
                                                <tr
                                                    key={
                                                        incident.id
                                                    }
                                                >
                                                    <td>
                                                        <Link
                                                            className="table-link"
                                                            to={
                                                                `/incidents/${incident.id}`
                                                            }
                                                        >
                                                            {
                                                                incident.title
                                                            }
                                                        </Link>
                                                    </td>

                                                    <td>
                                                        <StatusBadge
                                                            value={
                                                                incident.priority
                                                            }
                                                        />
                                                    </td>

                                                    <td>
                                                        <StatusBadge
                                                            value={
                                                                incident.status
                                                            }
                                                        />
                                                    </td>

                                                    <td>
                                                        {
                                                            incident.source
                                                        }
                                                    </td>

                                                    <td>
                                                        <code>
                                                            {
                                                                shortIdentifier(
                                                                    incident
                                                                        .correlationId
                                                                )
                                                            }
                                                        </code>
                                                    </td>

                                                    <td>
                                                        {
                                                            formatDate(
                                                                incident.createdAt
                                                            )
                                                        }
                                                    </td>
                                                </tr>
                                            )
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

export function IncidentDetailPage() {
    const {
        incidentId
    } = useParams();

    const {
        accessToken,
        user
    } = useAuth();

    const [
        incident,
        setIncident
    ] = useState<Incident | null>(
        null
    );

    const [
        linkedAlert,
        setLinkedAlert
    ] = useState<Alert | null>(
        null
    );

    const [
        error,
        setError
    ] = useState<string | null>(
        null
    );

    const [
        actionError,
        setActionError
    ] = useState<string | null>(
        null
    );

    const [
        runningAction,
        setRunningAction
    ] = useState<
        IncidentAction | null
    >(
        null
    );

    useEffect(
        () => {
            if (
                accessToken === null
                || incidentId === undefined
            ) {
                return;
            }

            const token =
                accessToken;

            const resolvedIncidentId =
                incidentId;

            let cancelled = false;

            async function load():
                Promise<void> {
                try {
                    const loadedIncident =
                        await getIncident(
                            token,
                            resolvedIncidentId
                        );

                    let alert:
                        Alert | null =
                        null;

                    if (
                        loadedIncident
                            .sourceAlertId
                        !== null
                    ) {
                        try {
                            alert =
                                await getAlert(
                                    token,
                                    loadedIncident
                                        .sourceAlertId
                                );
                        } catch {
                            alert = null;
                        }
                    }

                    if (!cancelled) {
                        setIncident(
                            loadedIncident
                        );

                        setLinkedAlert(
                            alert
                        );

                        setError(null);
                    }
                } catch {
                    if (!cancelled) {
                        setError(
                            "The incident could not be loaded."
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
            incidentId
        ]
    );

    async function runTransition(
        action: IncidentAction
    ): Promise<void> {
        if (
            accessToken === null
            || incident === null
        ) {
            return;
        }

        const token =
            accessToken;

        const currentIncident =
            incident;

        setRunningAction(action);
        setActionError(null);

        try {
            const updated =
                await transitionIncident(
                    token,
                    currentIncident.id,
                    action
                );

            setIncident(updated);
        } catch {
            setActionError(
                "The incident action could not be completed."
            );
        } finally {
            setRunningAction(null);
        }
    }

    if (error !== null) {
        return (
            <StatePanel
                title="Unable to load Incident"
                message={error}
            />
        );
    }

    if (incident === null) {
        return (
            <StatePanel
                title="Loading Incident"
                message="Reading incident and linked monitoring context."
            />
        );
    }

    const operator =
        canOperateIncidents(
            user?.roles ?? []
        );

    const transitions =
        getIncidentTransitions(
            incident.status
        );

    return (
        <>
            <Link
                className="back-link"
                to="/incidents"
            >
                ← Back to incidents
            </Link>

            <PageHeader
                title={incident.title}
                description={
                    incident.description
                }
                actions={
                    <div
                        className="status-cluster"
                    >
                        <StatusBadge
                            value={
                                incident.priority
                            }
                        />

                        <StatusBadge
                            value={
                                incident.status
                            }
                        />
                    </div>
                }
            />

            {
                operator
                && transitions.length > 0
                    ? (
                        <div
                            className="action-bar"
                        >
                            {
                                transitions.map(
                                    transition => (
                                        <button
                                            key={
                                                transition.action
                                            }
                                            type="button"
                                            className="button button--primary"
                                            disabled={
                                                runningAction
                                                !== null
                                            }
                                            onClick={
                                                () => {
                                                    void runTransition(
                                                        transition.action
                                                    );
                                                }
                                            }
                                        >
                                            {
                                                runningAction
                                                === transition.action
                                                    ? "Working…"
                                                    : transition.label
                                            }
                                        </button>
                                    )
                                )
                            }
                        </div>
                    )
                    : null
            }

            {
                actionError !== null
                    ? (
                        <div
                            className="inline-error"
                            role="alert"
                        >
                            {actionError}
                        </div>
                    )
                    : null
            }

            <section
                className="panel"
            >
                <dl
                    className="detail-grid"
                >
                    <DetailItem
                        label="Incident ID"
                    >
                        <code>
                            {incident.id}
                        </code>
                    </DetailItem>

                    <DetailItem
                        label="Source"
                    >
                        {incident.source}
                    </DetailItem>

                    <DetailItem
                        label="Impact"
                    >
                        {incident.impact}
                    </DetailItem>

                    <DetailItem
                        label="Urgency"
                    >
                        {incident.urgency}
                    </DetailItem>

                    <DetailItem
                        label="Assignee"
                    >
                        {
                            incident.assigneeId
                            ?? "—"
                        }
                    </DetailItem>

                    <DetailItem
                        label="Team"
                    >
                        {
                            incident.teamId
                            ?? "—"
                        }
                    </DetailItem>

                    <DetailItem
                        label="Correlation ID"
                    >
                        {
                            incident.correlationId
                                ? (
                                    <code>
                                        {
                                            incident.correlationId
                                        }
                                    </code>
                                )
                                : "—"
                        }
                    </DetailItem>

                    <DetailItem
                        label="Created"
                    >
                        {
                            formatDate(
                                incident.createdAt
                            )
                        }
                    </DetailItem>

                    <DetailItem
                        label="Updated"
                    >
                        {
                            formatDate(
                                incident.updatedAt
                            )
                        }
                    </DetailItem>
                </dl>
            </section>

            {
                incident.source
                === "MONITORING"
                    ? (
                        <section
                            className="panel linked-panel"
                        >
                            <h2>
                                Monitoring context
                            </h2>

                            <dl
                                className="detail-grid"
                            >
                                <DetailItem
                                    label="Source alert ID"
                                >
                                    {
                                        incident.sourceAlertId
                                            ? (
                                                <code>
                                                    {
                                                        incident.sourceAlertId
                                                    }
                                                </code>
                                            )
                                            : "—"
                                    }
                                </DetailItem>

                                <DetailItem
                                    label="Monitoring recovered"
                                >
                                    {
                                        formatDate(
                                            incident
                                                .monitoringRecoveredAt
                                        )
                                    }
                                </DetailItem>

                                <DetailItem
                                    label="Recovery message"
                                >
                                    {
                                        incident
                                            .monitoringRecoveryMessage
                                        ?? "—"
                                    }
                                </DetailItem>
                            </dl>

                            {
                                linkedAlert !== null
                                    ? (
                                        <div
                                            className="linked-resource"
                                        >
                                            <div>
                                                <strong>
                                                    {
                                                        linkedAlert.message
                                                    }
                                                </strong>

                                                <span>
                                                    OpsSight alert
                                                </span>
                                            </div>

                                            <Link
                                                className="button button--secondary"
                                                to={
                                                    `/alerts/${linkedAlert.id}`
                                                }
                                            >
                                                View alert
                                            </Link>
                                        </div>
                                    )
                                    : null
                            }
                        </section>
                    )
                    : null
            }
        </>
    );
}