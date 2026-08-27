import {
    describe,
    expect,
    it
} from "vitest";

import type {
    Check,
    Host
} from "./api";

import {
    canOperateIncidents,
    getHostOperationalStatus,
    getIncidentTransitions
} from "./domain";

const host: Host = {
    id: "host-1",
    name: "app-01",
    address: "10.0.0.10",
    enabled: true,
    created_at:
        "2026-08-27T08:00:00Z"
};

const healthyCheck: Check = {
    id: "check-1",
    host_id: host.id,
    name: "HTTPS",
    check_type: "https",
    target:
        "https://example.test",
    interval_seconds: 60,
    timeout_seconds: 5,
    consecutive_failures: 0,
    next_run_at:
        "2026-08-27T08:01:00Z",
    enabled: true,
    created_at:
        "2026-08-27T08:00:00Z"
};

describe(
    "operator domain rules",
    () => {
        it(
            "marks a host degraded when an enabled check is failing",
            () => {
                expect(
                    getHostOperationalStatus(
                        host,
                        [
                            {
                                ...healthyCheck,
                                consecutive_failures:
                                    2
                            }
                        ]
                    )
                ).toBe(
                    "degraded"
                );
            }
        );

        it(
            "allows technician and higher operational roles to act on incidents",
            () => {
                expect(
                    canOperateIncidents([
                        "technician"
                    ])
                ).toBe(true);

                expect(
                    canOperateIncidents([
                        "viewer"
                    ])
                ).toBe(false);
            }
        );

        it(
            "exposes only valid incident lifecycle actions",
            () => {
                expect(
                    getIncidentTransitions(
                        "OPEN"
                    )
                ).toEqual([
                    {
                        action:
                            "start-progress",
                        label:
                            "Start progress"
                    }
                ]);

                expect(
                    getIncidentTransitions(
                        "CLOSED"
                    )
                ).toEqual([]);
            }
        );
    }
);