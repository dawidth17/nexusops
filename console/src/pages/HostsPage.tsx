import {
    useEffect,
    useMemo,
    useState
} from "react";

import {
    type Check,
    type Host,
    listChecks,
    listHosts
} from "../api";

import { useAuth } from "../auth";

import {
    getHostOperationalStatus
} from "../domain";

import {
    PageHeader,
    StatePanel,
    StatusBadge
} from "../ui";

interface HostData {
    hosts: Host[];
    checks: Check[];
}

export function HostsPage() {
    const {
        accessToken
    } = useAuth();

    const [
        data,
        setData
    ] = useState<HostData | null>(
        null
    );

    const [
        query,
        setQuery
    ] = useState("");

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
                        checks
                    ] =
                        await Promise.all([
                            listHosts(
                                token
                            ),
                            listChecks(
                                token
                            )
                        ]);

                    if (!cancelled) {
                        setData({
                            hosts,
                            checks
                        });

                        setError(null);
                    }
                } catch {
                    if (!cancelled) {
                        setError(
                            "Monitored hosts could not be loaded."
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

    const filteredHosts =
        useMemo(
            () => {
                if (data === null) {
                    return [];
                }

                const normalized =
                    query
                        .trim()
                        .toLowerCase();

                if (!normalized) {
                    return data.hosts;
                }

                return data.hosts.filter(
                    host =>
                        host.name
                            .toLowerCase()
                            .includes(normalized)
                        || host.address
                            .toLowerCase()
                            .includes(normalized)
                );
            },
            [
                data,
                query
            ]
        );

    if (error !== null) {
        return (
            <StatePanel
                title="Unable to load Hosts"
                message={error}
            />
        );
    }

    if (data === null) {
        return (
            <StatePanel
                title="Loading Hosts"
                message="Reading monitored hosts and configured checks."
            />
        );
    }

    return (
        <>
            <PageHeader
                title="Hosts"
                description="Monitored systems and their current check state."
            />

            <div
                className="toolbar"
            >
                <label>
                    <span>
                        Search hosts
                    </span>

                    <input
                        type="search"
                        value={query}
                        placeholder="Name or address"
                        onChange={
                            event =>
                                setQuery(
                                    event.target.value
                                )
                        }
                    />
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
                                    Host
                                </th>

                                <th>
                                    Address
                                </th>

                                <th>
                                    Status
                                </th>

                                <th>
                                    Checks
                                </th>

                                <th>
                                    Failing
                                </th>
                            </tr>
                        </thead>

                        <tbody>
                            {
                                filteredHosts.length
                                === 0
                                    ? (
                                        <tr>
                                            <td
                                                colSpan={
                                                    5
                                                }
                                                className="empty-cell"
                                            >
                                                No hosts match the current filter.
                                            </td>
                                        </tr>
                                    )
                                    : (
                                        filteredHosts.map(
                                            host => {
                                                const checks =
                                                    data.checks.filter(
                                                        check =>
                                                            check.host_id
                                                            === host.id
                                                    );

                                                const failing =
                                                    checks.filter(
                                                        check =>
                                                            check.enabled
                                                            && check
                                                                .consecutive_failures
                                                            > 0
                                                    ).length;

                                                const status =
                                                    getHostOperationalStatus(
                                                        host,
                                                        data.checks
                                                    );

                                                return (
                                                    <tr
                                                        key={
                                                            host.id
                                                        }
                                                    >
                                                        <td>
                                                            <strong>
                                                                {
                                                                    host.name
                                                                }
                                                            </strong>
                                                        </td>

                                                        <td>
                                                            {
                                                                host.address
                                                            }
                                                        </td>

                                                        <td>
                                                            <StatusBadge
                                                                value={
                                                                    status
                                                                }
                                                            />
                                                        </td>

                                                        <td>
                                                            {
                                                                checks.length
                                                            }
                                                        </td>

                                                        <td>
                                                            {
                                                                failing
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