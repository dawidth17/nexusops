CREATE TABLE asset_assignments (
    id UUID PRIMARY KEY,

    asset_id UUID NOT NULL,
    assignee_id VARCHAR(255) NOT NULL,

    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    returned_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_asset_assignments_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets(id),

    CONSTRAINT chk_asset_assignments_time_order
        CHECK (
            returned_at IS NULL
            OR returned_at >= assigned_at
        )
);

CREATE UNIQUE INDEX uq_asset_assignments_active_asset
    ON asset_assignments (asset_id)
    WHERE returned_at IS NULL;

CREATE INDEX idx_asset_assignments_asset_history
    ON asset_assignments (
        asset_id,
        assigned_at DESC
    );

CREATE INDEX idx_asset_assignments_assignee
    ON asset_assignments (assignee_id);