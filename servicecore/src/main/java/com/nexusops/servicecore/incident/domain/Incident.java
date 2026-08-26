package com.nexusops.servicecore.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSource source;

    @Column(name = "source_alert_id")
    private UUID sourceAlertId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "monitoring_recovered_at")
    private Instant monitoringRecoveredAt;

    @Column(
            name = "monitoring_recovery_message",
            columnDefinition = "TEXT"
    )
    private String monitoringRecoveryMessage;

    @Column(name = "assignee_id", length = 255)
    private String assigneeId;

    @Column(name = "team_id", length = 255)
    private String teamId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    private Incident(
            String title,
            String description,
            Impact impact,
            Urgency urgency
    ) {
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");
        this.impact = Objects.requireNonNull(
                impact,
                "impact must not be null"
        );
        this.urgency = Objects.requireNonNull(
                urgency,
                "urgency must not be null"
        );
        this.priority = PriorityCalculator.calculate(
                impact,
                urgency
        );
        this.status = IncidentStatus.OPEN;
        this.source = IncidentSource.MANUAL;
    }

    private Incident(
            String title,
            String description,
            Impact impact,
            Urgency urgency,
            UUID sourceAlertId,
            String correlationId
    ) {
        this(
                title,
                description,
                impact,
                urgency
        );

        this.source = IncidentSource.MONITORING;

        this.sourceAlertId = Objects.requireNonNull(
                sourceAlertId,
                "sourceAlertId must not be null"
        );

        this.correlationId = requireText(
                correlationId,
                "correlationId"
        );
    }

    public static Incident create(
            String title,
            String description,
            Impact impact,
            Urgency urgency
    ) {
        return new Incident(
                title,
                description,
                impact,
                urgency
        );
    }

    public static Incident createFromMonitoring(
            String title,
            String description,
            Impact impact,
            Urgency urgency,
            UUID sourceAlertId,
            String correlationId
    ) {
        return new Incident(
                title,
                description,
                impact,
                urgency,
                sourceAlertId,
                correlationId
        );
    }

    public void recordMonitoringRecovery(
            Instant recoveredAt,
            String recoveryMessage
    ) {
        if (source != IncidentSource.MONITORING) {
            throw new IllegalStateException(
                    "only monitoring incidents can record monitoring recovery"
            );
        }

        monitoringRecoveredAt = Objects.requireNonNull(
                recoveredAt,
                "recoveredAt must not be null"
        );

        monitoringRecoveryMessage = requireText(
                recoveryMessage,
                "recoveryMessage"
        );
    }

    public void updateAssessment(
            Impact impact,
            Urgency urgency
    ) {
        this.impact = Objects.requireNonNull(
                impact,
                "impact must not be null"
        );
        this.urgency = Objects.requireNonNull(
                urgency,
                "urgency must not be null"
        );
        this.priority = PriorityCalculator.calculate(
                impact,
                urgency
        );
    }

    public void assignToTeam(String teamId) {
        this.teamId = requireText(
                teamId,
                "teamId"
        );
    }

    public void assignToUser(String assigneeId) {
        this.assigneeId = requireText(
                assigneeId,
                "assigneeId"
        );
    }

    public void clearAssignee() {
        this.assigneeId = null;
    }

    public void clearTeam() {
        this.teamId = null;
    }

    public void startProgress() {
        transitionTo(
                IncidentStatus.IN_PROGRESS
        );
    }

    public void returnToOpen() {
        transitionTo(
                IncidentStatus.OPEN
        );
    }

    public void resolve() {
        transitionTo(
                IncidentStatus.RESOLVED
        );
    }

    public void reopen() {
        transitionTo(
                IncidentStatus.IN_PROGRESS
        );
    }

    public void close() {
        transitionTo(
                IncidentStatus.CLOSED
        );
    }

    private void transitionTo(
            IncidentStatus targetStatus
    ) {
        if (!canTransitionTo(targetStatus)) {
            throw new InvalidIncidentTransitionException(
                    status,
                    targetStatus
            );
        }

        status = targetStatus;
    }

    private boolean canTransitionTo(
            IncidentStatus targetStatus
    ) {
        return switch (status) {
            case OPEN ->
                    targetStatus
                            == IncidentStatus.IN_PROGRESS;

            case IN_PROGRESS ->
                    targetStatus
                            == IncidentStatus.OPEN
                            || targetStatus
                            == IncidentStatus.RESOLVED;

            case RESOLVED ->
                    targetStatus
                            == IncidentStatus.IN_PROGRESS
                            || targetStatus
                            == IncidentStatus.CLOSED;

            case CLOSED -> false;
        };
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public Priority getPriority() {
        return priority;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public IncidentSource getSource() {
        return source;
    }

    public UUID getSourceAlertId() {
        return sourceAlertId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getMonitoringRecoveredAt() {
        return monitoringRecoveredAt;
    }

    public String getMonitoringRecoveryMessage() {
        return monitoringRecoveryMessage;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public String getTeamId() {
        return teamId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
