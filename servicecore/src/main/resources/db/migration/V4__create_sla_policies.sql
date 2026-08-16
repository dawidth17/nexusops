CREATE TABLE sla_policies (
    priority VARCHAR(10) PRIMARY KEY,
    first_response_target_minutes BIGINT NOT NULL,
    resolution_target_minutes BIGINT NOT NULL,
    calendar_type VARCHAR(50) NOT NULL,

    CONSTRAINT chk_sla_policies_priority
        CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),

    CONSTRAINT chk_sla_policies_first_response_target
        CHECK (first_response_target_minutes > 0),

    CONSTRAINT chk_sla_policies_resolution_target
        CHECK (resolution_target_minutes > 0),

    CONSTRAINT chk_sla_policies_target_order
        CHECK (
            resolution_target_minutes
                >= first_response_target_minutes
        ),

    CONSTRAINT chk_sla_policies_calendar_type
        CHECK (
            calendar_type IN ('TWENTY_FOUR_SEVEN')
        )
);

INSERT INTO sla_policies (
    priority,
    first_response_target_minutes,
    resolution_target_minutes,
    calendar_type
)
VALUES
    ('P1', 15, 240, 'TWENTY_FOUR_SEVEN'),
    ('P2', 30, 480, 'TWENTY_FOUR_SEVEN'),
    ('P3', 240, 1440, 'TWENTY_FOUR_SEVEN'),
    ('P4', 480, 4320, 'TWENTY_FOUR_SEVEN');