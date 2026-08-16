CREATE TABLE incident_slas (
    incident_id UUID PRIMARY KEY,

    first_response_due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolution_due_at TIMESTAMP WITH TIME ZONE NOT NULL,

    first_responded_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,

    response_breached_at TIMESTAMP WITH TIME ZONE,
    resolution_breached_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_incident_slas_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents(id),

    CONSTRAINT chk_incident_slas_deadline_order
        CHECK (
            resolution_due_at >= first_response_due_at
        ),

    CONSTRAINT chk_incident_slas_response_breach
        CHECK (
            response_breached_at IS NULL
            OR response_breached_at = first_response_due_at
        ),

    CONSTRAINT chk_incident_slas_resolution_breach
        CHECK (
            resolution_breached_at IS NULL
            OR resolution_breached_at = resolution_due_at
        )
);

CREATE INDEX idx_incident_slas_pending_response
    ON incident_slas (first_response_due_at)
    WHERE first_responded_at IS NULL
      AND response_breached_at IS NULL;

CREATE INDEX idx_incident_slas_pending_resolution
    ON incident_slas (resolution_due_at)
    WHERE resolved_at IS NULL
      AND resolution_breached_at IS NULL;