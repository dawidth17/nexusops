import {
    expect,
    test
} from "@playwright/test";

function requiredEnvironment(
    name: string
): string {
    const value =
        process.env[name];

    if (
        value === undefined
        || value.length === 0
    ) {
        throw new Error(
            `${name} is required`
        );
    }

    return value;
}

test(
    "recovered monitoring context is visible in the live Console",
    async ({
        page
    }) => {
        const username =
            requiredEnvironment(
                "NEXUSOPS_E2E_USER"
            );

        const password =
            requiredEnvironment(
                "NEXUSOPS_E2E_PASSWORD"
            );

        const hostName =
            requiredEnvironment(
                "NEXUSOPS_E2E_HOST_NAME"
            );

        const alertId =
            requiredEnvironment(
                "NEXUSOPS_E2E_ALERT_ID"
            );

        const incidentId =
            requiredEnvironment(
                "NEXUSOPS_E2E_INCIDENT_ID"
            );

        const correlationId =
            requiredEnvironment(
                "NEXUSOPS_E2E_CORRELATION_ID"
            );

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

        await page.getByRole(
            "button",
            {
                name:
                    "Sign in"
            }
        ).click();

        await page.locator(
            "#username"
        ).fill(
            username
        );

        await page.locator(
            "#password"
        ).fill(
            password
        );

        await page.locator(
            "#kc-login"
        ).click();

        await expect(
            page.getByRole(
                "heading",
                {
                    name: "Overview"
                }
            )
        ).toBeVisible({
            timeout: 30000
        });

        await page.getByRole(
            "link",
            {
                name: "Hosts"
            }
        ).click();

        await expect(
            page.getByRole(
                "heading",
                {
                    name: "Hosts"
                }
            )
        ).toBeVisible();

        await expect(
            page
                .getByRole("table")
                .getByRole("row")
                .filter({
                    hasText: hostName
                })
                .first()
        ).toBeVisible();

        await page.goto(
            `/alerts/${alertId}`
        );

        await expect(
            page.getByRole(
                "heading",
                {
                    name:
                        "Alert details"
                }
            )
        ).toBeVisible();

        await expect(
            page.getByText(
                "RECOVERED",
                {
                    exact: true
                }
            )
        ).toBeVisible();

        await expect(
            page.getByText(
                correlationId,
                {
                    exact: true
                }
            )
        ).toBeVisible();

        const incidentLink =
            page.getByRole(
                "link",
                {
                    name:
                        "View incident"
                }
            );

        await expect(
            incidentLink
        ).toBeVisible();

        await incidentLink.click();

        await expect(
            page
        ).toHaveURL(
            new RegExp(
                `/incidents/${incidentId}$`
            )
        );

        await expect(
            page.getByRole(
                "heading",
                {
                    name:
                        "Monitoring context"
                }
            )
        ).toBeVisible();

        await expect(
            page.getByText(
                alertId,
                {
                    exact: true
                }
            )
        ).toBeVisible();

        await expect(
            page.getByText(
                correlationId,
                {
                    exact: true
                }
            )
        ).toBeVisible();

        await expect(
            page.getByRole(
                "link",
                {
                    name:
                        "View alert"
                }
            )
        ).toBeVisible();
    }
);