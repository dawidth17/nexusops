CREATE TABLE audit_entries (
    id UUID PRIMARY KEY,

    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,

    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,

    before_summary TEXT,
    after_summary TEXT,

    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id UUID,

    CONSTRAINT chk_audit_entries_actor
        CHECK (
            LENGTH(TRIM(actor_id)) > 0
        ),

    CONSTRAINT chk_audit_entries_entity_type
        CHECK (
            entity_type IN (
                'INCIDENT',
                'ASSET',
                'KNOWLEDGE_ARTICLE'
            )
        )
);

CREATE INDEX idx_audit_entries_entity
    ON audit_entries (
        entity_type,
        entity_id,
        occurred_at DESC
    );

CREATE INDEX idx_audit_entries_actor
    ON audit_entries (
        actor_id,
        occurred_at DESC
    );

CREATE INDEX idx_audit_entries_correlation
    ON audit_entries (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_audit_entry_mutation()
RETURNS TRIGGER
AS $$
BEGIN
    RAISE EXCEPTION 'audit_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_entries_no_update
BEFORE UPDATE ON audit_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_audit_entry_mutation();

CREATE TRIGGER trg_audit_entries_no_delete
BEFORE DELETE ON audit_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_audit_entry_mutation();