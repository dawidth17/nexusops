CREATE TABLE assets (
    id UUID PRIMARY KEY,

    asset_tag VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(150) NOT NULL,
    serial_number VARCHAR(150) NOT NULL,

    status VARCHAR(30) NOT NULL,
    assignee_id VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_assets_asset_tag
        UNIQUE (asset_tag),

    CONSTRAINT uq_assets_serial_number
        UNIQUE (serial_number),

    CONSTRAINT chk_assets_type
        CHECK (
            type IN (
                'LAPTOP',
                'DESKTOP',
                'SERVER',
                'MOBILE_PHONE',
                'NETWORK_DEVICE',
                'OTHER'
            )
        ),

    CONSTRAINT chk_assets_status
        CHECK (
            status IN (
                'AVAILABLE',
                'ASSIGNED',
                'MAINTENANCE',
                'RETIRED'
            )
        ),

    CONSTRAINT chk_assets_assignment_state
        CHECK (
            (
                status = 'ASSIGNED'
                AND assignee_id IS NOT NULL
            )
            OR
            (
                status <> 'ASSIGNED'
                AND assignee_id IS NULL
            )
        )
);

CREATE INDEX idx_assets_status
    ON assets (status);

CREATE INDEX idx_assets_type
    ON assets (type);

CREATE INDEX idx_assets_assignee
    ON assets (assignee_id)
    WHERE assignee_id IS NOT NULL;