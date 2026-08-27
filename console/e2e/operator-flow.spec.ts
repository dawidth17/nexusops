import {
    expect,
    test
} from "@playwright/test";

const alertId =
    "11111111-1111-4111-8111-111111111111";

const incidentId =
    "22222222-2222-4222-8222-222222222222";

const correlationId =
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

function createAccessToken(): string {
    const header =
        Buffer.from(
            JSON.stringify({
                alg: "none",
                typ: "JWT"
            })
        ).toString(
            "base64url"
        );

    const payload =
        Buffer.from(
            JSON.stringify({
                sub: "operator-1",
                preferred_username:
                    "nexusops-dev",
                exp:
                    Math.floor(
                        Date.now() / 1000
                    ) + 3600,
                realm_access: {
                    roles: [
                        "viewer",
                        "operator",
                        "employee",
                        "technician"
                    ]
                }
            })
        ).toString(
            "base64url"
        );

    return `${header}.${payload}.signature`;
}

test(
    "protected routes require authentication",
    async ({
        page
    }) => {
        await page.goto(
            "/alerts"
        );

        await expect(
            page.getByRole(
                "heading",
                {
                    name:
                        "Sign in to NexusOps"
                }
            )
        ).toBeVisible();
    }
);

test(
    "operator follows an alert into its incident and starts progress",
    async ({
        page
    }) => {
        let incident = {
            id: incidentId,
            title:
                "API latency threshold exceeded",
            description:
                "Created from OpsSight alert",
            impact: "HIGH",
            urgency: "HIGH",
            priority: "P1",
            status: "OPEN",
            source: "MONITORING",
            sourceAlertId: alertId,
            correlationId,
            monitoringRecoveredAt:
                null,
            monitoringRecoveryMessage:
                null,
            assigneeId: null,
            teamId: null,
            createdAt:
                "2026-08-27T08:01:00Z",
            updatedAt:
                "2026-08-27T08:01:00Z"
        };

        const alert = {
            id: alertId,
            alert_rule_id:
                "33333333-3333-4333-8333-333333333333",
            status: "open",
            correlation_id:
                correlationId,
            message:
                "API latency threshold exceeded",
            opened_at:
                "2026-08-27T08:00:00Z",
            recovered_at:
                null
        };

        await page.addInitScript(
            token => {
                sessionStorage.setItem(
                    "nexusops.access_token",
                    token
                );
            },
            createAccessToken()
        );

        await page.route(
            "**/opssight/api/v1/**",
            async route => {
                const url =
                    new URL(
                        route.request().url()
                    );

                if (
                    url.pathname.endsWith(
                        `/alerts/${alertId}`
                    )
                ) {
                    await route.fulfill({
                        status: 200,
                        contentType:
                            "application/json",
                        body:
                            JSON.stringify(
                                alert
                            )
                    });

                    return;
                }

                if (
                    url.pathname.endsWith(
                        "/alerts"
                    )
                ) {
                    await route.fulfill({
                        status: 200,
                        contentType:
                            "application/json",
                        body:
                            JSON.stringify([
                                alert
                            ])
                    });

                    return;
                }

                await route.fulfill({
                    status: 200,
                    contentType:
                        "application/json",
                    body:
                        JSON.stringify([])
                });
            }
        );

        await page.route(
            "**/servicecore/api/v1/**",
            async route => {
                const request =
                    route.request();

                const url =
                    new URL(
                        request.url()
                    );

                if (
                    request.method()
                    === "POST"
                    && url.pathname.endsWith(
                        `/incidents/${incidentId}/start-progress`
                    )
                ) {
                    incident = {
                        ...incident,
                        status:
                            "IN_PROGRESS",
                        updatedAt:
                            "2026-08-27T08:02:00Z"
                    };

                    await route.fulfill({
                        status: 200,
                        contentType:
                            "application/json",
                        body:
                            JSON.stringify(
                                incident
                            )
                    });

                    return;
                }

                if (
                    url.pathname.endsWith(
                        `/incidents/${incidentId}`
                    )
                ) {
                    await route.fulfill({
                        status: 200,
                        contentType:
                            "application/json",
                        body:
                            JSON.stringify(
                                incident
                            )
                    });

                    return;
                }

                if (
                    url.pathname.includes(
                        "/incidents"
                    )
                ) {
                    await route.fulfill({
                        status: 200,
                        contentType:
                            "application/json",
                        body:
                            JSON.stringify({
                                content: [
                                    incident
                                ],
                                page: 0,
                                size: 1,
                                totalElements: 1,
                                totalPages: 1,
                                first: true,
                                last: true
                            })
                    });

                    return;
                }

                await route.fulfill({
                    status: 404
                });
            }
        );

        await page.goto(
            "/alerts"
        );

        await expect(
            page.getByRole(
                "heading",
                {
                    name: "Alerts"
                }
            )
        ).toBeVisible();

        await page
            .locator(
                `a[href="/alerts/${alertId}"]`
            )
            .click();

        await expect(
            page.getByRole(
                "heading",
                {
                    name:
                        "Alert details"
                }
            )
        ).toBeVisible();

        await page.getByRole(
            "link",
            {
                name:
                    "View incident"
            }
        ).click();

        await expect(
            page.getByRole(
                "heading",
                {
                    name:
                        "API latency threshold exceeded"
                }
            )
        ).toBeVisible();

        await page.getByRole(
            "button",
            {
                name:
                    "Start progress"
            }
        ).click();

        await expect(
            page.getByText(
                "IN PROGRESS",
                {
                    exact: true
                }
            )
        ).toBeVisible();
    }
);