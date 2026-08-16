CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    impact VARCHAR(20) NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_incidents_impact
        CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),

    CONSTRAINT chk_incidents_urgency
        CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH')),

    CONSTRAINT chk_incidents_priority
        CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),

    CONSTRAINT chk_incidents_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);