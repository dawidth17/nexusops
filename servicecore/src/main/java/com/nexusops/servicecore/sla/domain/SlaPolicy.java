package com.nexusops.servicecore.sla.domain;

import com.nexusops.servicecore.incident.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(
            name = "first_response_target_minutes",
            nullable = false
    )
    private long firstResponseTargetMinutes;

    @Column(
            name = "resolution_target_minutes",
            nullable = false
    )
    private long resolutionTargetMinutes;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "calendar_type",
            nullable = false,
            length = 50
    )
    private SlaCalendarType calendarType;

    protected SlaPolicy() {
    }

    public SlaPolicy(
            Priority priority,
            long firstResponseTargetMinutes,
            long resolutionTargetMinutes,
            SlaCalendarType calendarType
    ) {
        if (priority == null) {
            throw new IllegalArgumentException(
                    "priority must not be null"
            );
        }

        if (firstResponseTargetMinutes <= 0) {
            throw new IllegalArgumentException(
                    "firstResponseTargetMinutes must be positive"
            );
        }

        if (resolutionTargetMinutes <= 0) {
            throw new IllegalArgumentException(
                    "resolutionTargetMinutes must be positive"
            );
        }

        if (resolutionTargetMinutes < firstResponseTargetMinutes) {
            throw new IllegalArgumentException(
                    "resolution target must not be shorter "
                            + "than first response target"
            );
        }

        if (calendarType == null) {
            throw new IllegalArgumentException(
                    "calendarType must not be null"
            );
        }

        this.priority = priority;
        this.firstResponseTargetMinutes =
                firstResponseTargetMinutes;
        this.resolutionTargetMinutes =
                resolutionTargetMinutes;
        this.calendarType = calendarType;
    }

    public Priority getPriority() {
        return priority;
    }

    public long getFirstResponseTargetMinutes() {
        return firstResponseTargetMinutes;
    }

    public long getResolutionTargetMinutes() {
        return resolutionTargetMinutes;
    }

    public SlaCalendarType getCalendarType() {
        return calendarType;
    }
}