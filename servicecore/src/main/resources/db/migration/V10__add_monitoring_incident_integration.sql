ALTER TABLE incidents
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_alert_id UUID,
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN monitoring_recovered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN monitoring_recovery_message TEXT;

ALTER TABLE incidents
    ALTER COLUMN source DROP DEFAULT;

ALTER TABLE incidents
    ADD CONSTRAINT chk_incidents_source
        CHECK (source IN ('MANUAL', 'MONITORING'));

ALTER TABLE incidents
    ADD CONSTRAINT chk_incidents_monitoring_identity
        CHECK (
            (
                source = 'MANUAL'
                AND source_alert_id IS NULL
                AND correlation_id IS NULL
            )
            OR
            (
                source = 'MONITORING'
                AND source_alert_id IS NOT NULL
                AND correlation_id IS NOT NULL
            )
        );

CREATE UNIQUE INDEX uq_incidents_monitoring_source_alert
    ON incidents (source_alert_id)
    WHERE source = 'MONITORING'
        AND source_alert_id IS NOT NULL;

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    source VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_processed_events_processed_at
    ON processed_events (processed_at);
