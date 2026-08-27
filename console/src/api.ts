import { config } from "./config";

export interface Host {
    id: string;
    name: string;
    address: string;
    enabled: boolean;
    created_at: string;
}

export interface Check {
    id: string;
    host_id: string;
    name: string;
    check_type: string;
    target: string;
    interval_seconds: number;
    timeout_seconds: number;
    consecutive_failures: number;
    next_run_at: string;
    enabled: boolean;
    created_at: string;
}

export interface Alert {
    id: string;
    alert_rule_id: string;
    status: "open" | "recovered";
    correlation_id: string;
    message: string;
    opened_at: string;
    recovered_at: string | null;
}

export type IncidentStatus =
    | "OPEN"
    | "IN_PROGRESS"
    | "RESOLVED"
    | "CLOSED";

export type IncidentSource =
    | "MANUAL"
    | "MONITORING";

export interface Incident {
    id: string;
    title: string;
    description: string;
    impact: "LOW" | "MEDIUM" | "HIGH";
    urgency: "LOW" | "MEDIUM" | "HIGH";
    priority: "P1" | "P2" | "P3" | "P4";
    status: IncidentStatus;
    source: IncidentSource;
    sourceAlertId: string | null;
    correlationId: string | null;
    monitoringRecoveredAt: string | null;
    monitoringRecoveryMessage: string | null;
    assigneeId: string | null;
    teamId: string | null;
    createdAt: string;
    updatedAt: string;
}

interface IncidentPage {
    content: Incident[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export type IncidentAction =
    | "start-progress"
    | "return-to-open"
    | "resolve"
    | "reopen"
    | "close";

export class ApiError
    extends Error {
    constructor(
        message: string,
        public readonly status: number
    ) {
        super(message);
        this.name = "ApiError";
    }
}

async function request<T>(
    path: string,
    accessToken: string,
    init: RequestInit = {}
): Promise<T> {
    const headers =
        new Headers(init.headers);

    headers.set(
        "Accept",
        "application/json"
    );

    headers.set(
        "Authorization",
        `Bearer ${accessToken}`
    );

    if (
        init.body !== undefined
        && !headers.has(
            "Content-Type"
        )
    ) {
        headers.set(
            "Content-Type",
            "application/json"
        );
    }

    let response: Response;

    try {
        response =
            await fetch(
                path,
                {
                    ...init,
                    headers
                }
            );
    } catch {
        throw new ApiError(
            "The NexusOps service could not be reached.",
            0
        );
    }

    if (!response.ok) {
        if (
            response.status === 401
        ) {
            throw new ApiError(
                "Your session is no longer authorized for this request.",
                401
            );
        }

        if (
            response.status === 403
        ) {
            throw new ApiError(
                "You do not have permission to perform this operation.",
                403
            );
        }

        if (
            response.status === 404
        ) {
            throw new ApiError(
                "The requested NexusOps resource was not found.",
                404
            );
        }

        if (
            response.status >= 500
        ) {
            throw new ApiError(
                "The NexusOps service reported an internal error.",
                response.status
            );
        }

        throw new ApiError(
            `The request could not be completed (${response.status}).`,
            response.status
        );
    }

    if (
        response.status === 204
    ) {
        return undefined as T;
    }

    return await response.json() as T;
}

export function listHosts(
    accessToken: string
): Promise<Host[]> {
    return request<Host[]>(
        `${config.opsSightBaseUrl}/hosts`,
        accessToken
    );
}

export function listChecks(
    accessToken: string
): Promise<Check[]> {
    return request<Check[]>(
        `${config.opsSightBaseUrl}/checks`,
        accessToken
    );
}

export function listAlerts(
    accessToken: string
): Promise<Alert[]> {
    return request<Alert[]>(
        `${config.opsSightBaseUrl}/alerts`,
        accessToken
    );
}

export function getAlert(
    accessToken: string,
    alertId: string
): Promise<Alert> {
    return request<Alert>(
        `${config.opsSightBaseUrl}/alerts/${encodeURIComponent(alertId)}`,
        accessToken
    );
}

export async function listIncidents(
    accessToken: string
): Promise<Incident[]> {
    const page =
        await request<IncidentPage>(
            `${config.serviceCoreBaseUrl}/incidents?size=100&sort=createdAt,desc`,
            accessToken
        );

    return page.content;
}

export function getIncident(
    accessToken: string,
    incidentId: string
): Promise<Incident> {
    return request<Incident>(
        `${config.serviceCoreBaseUrl}/incidents/${encodeURIComponent(incidentId)}`,
        accessToken
    );
}

export function transitionIncident(
    accessToken: string,
    incidentId: string,
    action: IncidentAction
): Promise<Incident> {
    return request<Incident>(
        `${config.serviceCoreBaseUrl}/incidents/${encodeURIComponent(incidentId)}/${action}`,
        accessToken,
        {
            method: "POST"
        }
    );
}