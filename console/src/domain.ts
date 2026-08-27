import type {
    Check,
    Host,
    IncidentAction,
    IncidentStatus
} from "./api";

export type HostOperationalStatus =
    | "healthy"
    | "degraded"
    | "unmonitored"
    | "disabled";

export interface IncidentTransition {
    action: IncidentAction;
    label: string;
}

export function getHostOperationalStatus(
    host: Host,
    checks: Check[]
): HostOperationalStatus {
    if (!host.enabled) {
        return "disabled";
    }

    const hostChecks =
        checks.filter(
            check =>
                check.host_id
                === host.id
        );

    const enabledChecks =
        hostChecks.filter(
            check =>
                check.enabled
        );

    if (
        enabledChecks.length === 0
    ) {
        return "unmonitored";
    }

    if (
        enabledChecks.some(
            check =>
                check
                    .consecutive_failures
                > 0
        )
    ) {
        return "degraded";
    }

    return "healthy";
}

export function canOperateIncidents(
    roles: string[]
): boolean {
    const allowed =
        new Set([
            "technician",
            "manager",
            "admin"
        ]);

    return roles.some(
        role =>
            allowed.has(
                role.toLowerCase()
            )
    );
}

export function getIncidentTransitions(
    status: IncidentStatus
): IncidentTransition[] {
    switch (status) {
        case "OPEN":
            return [
                {
                    action:
                        "start-progress",
                    label:
                        "Start progress"
                }
            ];

        case "IN_PROGRESS":
            return [
                {
                    action:
                        "return-to-open",
                    label:
                        "Return to open"
                },
                {
                    action:
                        "resolve",
                    label:
                        "Resolve"
                }
            ];

        case "RESOLVED":
            return [
                {
                    action:
                        "reopen",
                    label:
                        "Reopen"
                },
                {
                    action:
                        "close",
                    label:
                        "Close"
                }
            ];

        case "CLOSED":
            return [];
    }
}

export function formatStatus(
    value: string
): string {
    return value
        .replace(/_/g, " ")
        .toUpperCase();
}

export function formatDate(
    value: string | null
): string {
    if (value === null) {
        return "—";
    }

    const date =
        new Date(value);

    if (
        Number.isNaN(
            date.getTime()
        )
    ) {
        return value;
    }

    return new Intl.DateTimeFormat(
        "en",
        {
            dateStyle: "medium",
            timeStyle: "short"
        }
    ).format(date);
}

export function shortIdentifier(
    value: string | null,
    length = 12
): string {
    if (value === null) {
        return "—";
    }

    if (
        value.length <= length
    ) {
        return value;
    }

    return `${value.slice(0, length)}…`;
}