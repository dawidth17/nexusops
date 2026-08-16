CREATE TABLE incident_comments (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_incident_comments_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents(id)
);

CREATE INDEX idx_incident_comments_incident_created_at
    ON incident_comments (incident_id, created_at);