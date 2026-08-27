import type {
    ReactNode
} from "react";

import {
    formatStatus
} from "./domain";

export function StatusBadge(
    {
        value
    }: {
        value: string;
    }
) {
    const normalized =
        value
            .toLowerCase()
            .replace(/_/g, "-");

    let tone = "neutral";

    if (
        [
            "healthy",
            "recovered",
            "resolved",
            "closed"
        ].includes(normalized)
    ) {
        tone = "success";
    } else if (
        [
            "degraded",
            "warning",
            "p2",
            "p3",
            "in-progress"
        ].includes(normalized)
    ) {
        tone = "warning";
    } else if (
        [
            "open",
            "critical",
            "p1"
        ].includes(normalized)
    ) {
        tone = "danger";
    }

    return (
        <span
            className={
                `status-badge status-badge--${tone}`
            }
        >
            {formatStatus(value)}
        </span>
    );
}

export function StatePanel(
    {
        title,
        message
    }: {
        title: string;
        message: string;
    }
) {
    return (
        <section
            className="state-panel"
        >
            <h2>
                {title}
            </h2>

            <p>
                {message}
            </p>
        </section>
    );
}

export function PageHeader(
    {
        title,
        description,
        actions
    }: {
        title: string;
        description: string;
        actions?: ReactNode;
    }
) {
    return (
        <div
            className="page-header"
        >
            <div>
                <h1>
                    {title}
                </h1>

                <p>
                    {description}
                </p>
            </div>

            {
                actions !== undefined
                    ? (
                        <div
                            className="page-actions"
                        >
                            {actions}
                        </div>
                    )
                    : null
            }
        </div>
    );
}

export function StatCard(
    {
        label,
        value,
        description
    }: {
        label: string;
        value: number;
        description: string;
    }
) {
    return (
        <article
            className="stat-card"
        >
            <span
                className="stat-card__label"
            >
                {label}
            </span>

            <strong>
                {value}
            </strong>

            <span
                className="stat-card__description"
            >
                {description}
            </span>
        </article>
    );
}

export function DetailItem(
    {
        label,
        children
    }: {
        label: string;
        children: ReactNode;
    }
) {
    return (
        <div
            className="detail-item"
        >
            <dt>
                {label}
            </dt>

            <dd>
                {children}
            </dd>
        </div>
    );
}