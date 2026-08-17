package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentComment;
import com.nexusops.servicecore.incident.domain.Urgency;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class IncidentCommentRepositoryTests {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentCommentRepository incidentCommentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsComment() {
        Incident incident = incidentRepository.saveAndFlush(
                createIncident("Network issue")
        );

        IncidentComment comment = IncidentComment.create(
                incident,
                "user-123",
                "The network team is investigating."
        );

        IncidentComment savedComment =
                incidentCommentRepository.saveAndFlush(comment);

        UUID commentId = savedComment.getId();

        assertNotNull(commentId);
        assertNotNull(savedComment.getCreatedAt());

        entityManager.clear();

        IncidentComment loadedComment = incidentCommentRepository
                .findById(commentId)
                .orElseThrow();

        assertEquals(
                incident.getId(),
                loadedComment.getIncident().getId()
        );
        assertEquals("user-123", loadedComment.getAuthorId());
        assertEquals(
                "The network team is investigating.",
                loadedComment.getContent()
        );
        assertNotNull(loadedComment.getCreatedAt());
    }

    @Test
    void listsOnlyCommentsForRequestedIncident() {
        Incident firstIncident = incidentRepository.saveAndFlush(
                createIncident("First incident")
        );

        Incident secondIncident = incidentRepository.saveAndFlush(
                createIncident("Second incident")
        );

        incidentCommentRepository.saveAndFlush(
                IncidentComment.create(
                        firstIncident,
                        "user-123",
                        "First comment"
                )
        );

        incidentCommentRepository.saveAndFlush(
                IncidentComment.create(
                        firstIncident,
                        "user-456",
                        "Second comment"
                )
        );

        incidentCommentRepository.saveAndFlush(
                IncidentComment.create(
                        secondIncident,
                        "user-789",
                        "Comment for another incident"
                )
        );

        entityManager.clear();

        List<IncidentComment> comments =
                incidentCommentRepository
                        .findByIncident_IdOrderByCreatedAtAsc(
                                firstIncident.getId()
                        );

        assertEquals(2, comments.size());

        assertTrue(
                comments.stream().allMatch(
                        comment -> comment.getIncident()
                                .getId()
                                .equals(firstIncident.getId())
                )
        );

        assertTrue(
                comments.get(0)
                        .getCreatedAt()
                        .compareTo(comments.get(1).getCreatedAt()) <= 0
        );
    }

    private Incident createIncident(String title) {
        return Incident.create(
                title,
                "Test incident description",
                Impact.MEDIUM,
                Urgency.MEDIUM
        );
    }
}
