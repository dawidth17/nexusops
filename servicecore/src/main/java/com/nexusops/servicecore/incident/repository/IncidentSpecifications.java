package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> hasStatus(
            IncidentStatus status
    ) {
        if (status == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("status"),
                        status
                );
    }

    public static Specification<Incident> hasPriority(
            Priority priority
    ) {
        if (priority == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("priority"),
                        priority
                );
    }

    public static Specification<Incident> hasImpact(
            Impact impact
    ) {
        if (impact == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("impact"),
                        impact
                );
    }

    public static Specification<Incident> hasUrgency(
            Urgency urgency
    ) {
        if (urgency == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("urgency"),
                        urgency
                );
    }

    public static Specification<Incident> hasTeamId(
            String teamId
    ) {
        if (teamId == null || teamId.isBlank()) {
            return Specification.unrestricted();
        }

        String normalizedTeamId = teamId.trim();

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("teamId"),
                        normalizedTeamId
                );
    }

    public static Specification<Incident> hasAssigneeId(
            String assigneeId
    ) {
        if (assigneeId == null || assigneeId.isBlank()) {
            return Specification.unrestricted();
        }

        String normalizedAssigneeId = assigneeId.trim();

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("assigneeId"),
                        normalizedAssigneeId
                );
    }

    public static Specification<Incident> containsText(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return Specification.unrestricted();
        }

        String normalizedText = escapeLike(
                text.trim().toLowerCase(Locale.ROOT)
        );

        String pattern = "%" + normalizedText + "%";

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(
                                        root.<String>get("title")
                                ),
                                pattern,
                                '\\'
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(
                                        root.<String>get("description")
                                ),
                                pattern,
                                '\\'
                        )
                );
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
