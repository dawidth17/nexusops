package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentComment;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentCommentRepository;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentCommentServiceTests {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentCommentRepository incidentCommentRepository;

    @InjectMocks
    private IncidentCommentService incidentCommentService;

    @Test
    void addsCommentToIncident() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        when(incidentCommentRepository.save(any(IncidentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IncidentComment comment = incidentCommentService.addComment(
                incidentId,
                "user-123",
                "The network team is investigating."
        );

        assertEquals(incident, comment.getIncident());
        assertEquals("user-123", comment.getAuthorId());
        assertEquals(
                "The network team is investigating.",
                comment.getContent()
        );

        verify(incidentRepository).findById(incidentId);
        verify(incidentCommentRepository).save(comment);
    }

    @Test
    void listsCommentsForIncident() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        IncidentComment firstComment = IncidentComment.create(
                incident,
                "user-123",
                "First comment"
        );

        IncidentComment secondComment = IncidentComment.create(
                incident,
                "user-456",
                "Second comment"
        );

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        when(
                incidentCommentRepository
                        .findByIncident_IdOrderByCreatedAtAsc(incidentId)
        ).thenReturn(List.of(firstComment, secondComment));

        List<IncidentComment> comments =
                incidentCommentService.listComments(incidentId);

        assertEquals(2, comments.size());
        assertEquals("First comment", comments.get(0).getContent());
        assertEquals("Second comment", comments.get(1).getContent());

        verify(incidentRepository).findById(incidentId);
        verify(incidentCommentRepository)
                .findByIncident_IdOrderByCreatedAtAsc(incidentId);
    }

    @Test
    void rejectsCommentForMissingIncident() {
        UUID incidentId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.empty());

        assertThrows(
                IncidentNotFoundException.class,
                () -> incidentCommentService.addComment(
                        incidentId,
                        "user-123",
                        "Test comment"
                )
        );

        verify(incidentRepository).findById(incidentId);
        verifyNoInteractions(incidentCommentRepository);
    }

    @Test
    void rejectsListingCommentsForMissingIncident() {
        UUID incidentId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.empty());

        assertThrows(
                IncidentNotFoundException.class,
                () -> incidentCommentService.listComments(incidentId)
        );

        verify(incidentRepository).findById(incidentId);
        verifyNoInteractions(incidentCommentRepository);
    }

    private Incident createIncident() {
        return Incident.create(
                "Network issue",
                "Users cannot access internal services",
                Impact.MEDIUM,
                Urgency.MEDIUM
        );
    }
}
