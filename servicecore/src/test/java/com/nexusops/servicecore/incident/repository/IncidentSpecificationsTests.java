package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class IncidentSpecificationsTests {

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void combinesIncidentFilters() {
        String teamId = "team-" + UUID.randomUUID();
        String assigneeId = "user-" + UUID.randomUUID();

        Incident matchingIncident = createIncident(
                "Matching incident",
                Impact.MEDIUM,
                Urgency.HIGH
        );
        matchingIncident.assignToTeam(teamId);
        matchingIncident.assignToUser(assigneeId);

        Incident wrongAssignee = createIncident(
                "Wrong assignee",
                Impact.MEDIUM,
                Urgency.HIGH
        );
        wrongAssignee.assignToTeam(teamId);
        wrongAssignee.assignToUser("another-user");

        Incident wrongStatus = createIncident(
                "Wrong status",
                Impact.MEDIUM,
                Urgency.HIGH
        );
        wrongStatus.assignToTeam(teamId);
        wrongStatus.assignToUser(assigneeId);
        wrongStatus.startProgress();

        incidentRepository.saveAndFlush(matchingIncident);
        incidentRepository.saveAndFlush(wrongAssignee);
        incidentRepository.saveAndFlush(wrongStatus);

        Specification<Incident> specification =
                Specification.<Incident>unrestricted()
                        .and(
                                IncidentSpecifications.hasStatus(
                                        IncidentStatus.OPEN
                                )
                        )
                        .and(
                                IncidentSpecifications.hasPriority(
                                        Priority.P2
                                )
                        )
                        .and(
                                IncidentSpecifications.hasImpact(
                                        Impact.MEDIUM
                                )
                        )
                        .and(
                                IncidentSpecifications.hasUrgency(
                                        Urgency.HIGH
                                )
                        )
                        .and(
                                IncidentSpecifications.hasTeamId(
                                        teamId
                                )
                        )
                        .and(
                                IncidentSpecifications.hasAssigneeId(
                                        assigneeId
                                )
                        );

        Page<Incident> result = incidentRepository.findAll(
                specification,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(
                matchingIncident.getId(),
                result.getContent().get(0).getId()
        );
    }

    @Test
    void searchesTitleAndDescriptionCaseInsensitively() {
        String searchToken = (
                "SearchToken" + UUID.randomUUID()
        ).replace("-", "");

        Incident titleMatch = createIncident(
                "Network " + searchToken.toLowerCase(),
                Impact.LOW,
                Urgency.LOW
        );

        Incident descriptionMatch = Incident.create(
                "Another incident",
                "Problem related to "
                        + searchToken.toLowerCase(),
                Impact.LOW,
                Urgency.LOW
        );

        Incident unrelatedIncident = createIncident(
                "Unrelated incident",
                Impact.LOW,
                Urgency.LOW
        );

        incidentRepository.saveAndFlush(titleMatch);
        incidentRepository.saveAndFlush(descriptionMatch);
        incidentRepository.saveAndFlush(unrelatedIncident);

        Specification<Incident> specification =
                IncidentSpecifications.containsText(
                        searchToken.toUpperCase()
                );

        Page<Incident> result = incidentRepository.findAll(
                specification,
                PageRequest.of(0, 20)
        );

        Set<UUID> resultIds = result
                .getContent()
                .stream()
                .map(Incident::getId)
                .collect(Collectors.toSet());

        assertEquals(2, result.getTotalElements());
        assertTrue(resultIds.contains(titleMatch.getId()));
        assertTrue(resultIds.contains(descriptionMatch.getId()));
    }

    @Test
    void paginatesAndSortsIncidents() {
        String searchToken = (
                "PageToken" + UUID.randomUUID()
        ).replace("-", "");

        incidentRepository.saveAndFlush(
                createIncident(
                        "Alpha " + searchToken,
                        Impact.LOW,
                        Urgency.LOW
                )
        );

        incidentRepository.saveAndFlush(
                createIncident(
                        "Bravo " + searchToken,
                        Impact.LOW,
                        Urgency.LOW
                )
        );

        incidentRepository.saveAndFlush(
                createIncident(
                        "Charlie " + searchToken,
                        Impact.LOW,
                        Urgency.LOW
                )
        );

        incidentRepository.saveAndFlush(
                createIncident(
                        "Delta " + searchToken,
                        Impact.LOW,
                        Urgency.LOW
                )
        );

        incidentRepository.saveAndFlush(
                createIncident(
                        "Echo " + searchToken,
                        Impact.LOW,
                        Urgency.LOW
                )
        );

        Specification<Incident> specification =
                IncidentSpecifications.containsText(searchToken);

        PageRequest pageable = PageRequest.of(
                1,
                2,
                Sort.by(
                        Sort.Direction.ASC,
                        "title"
                )
        );

        Page<Incident> result = incidentRepository.findAll(
                specification,
                pageable
        );

        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(2, result.getContent().size());

        assertTrue(
                result.getContent()
                        .get(0)
                        .getTitle()
                        .startsWith("Charlie")
        );

        assertTrue(
                result.getContent()
                        .get(1)
                        .getTitle()
                        .startsWith("Delta")
        );
    }

    private Incident createIncident(
            String title,
            Impact impact,
            Urgency urgency
    ) {
        return Incident.create(
                title,
                "Test incident description",
                impact,
                urgency
        );
    }
}