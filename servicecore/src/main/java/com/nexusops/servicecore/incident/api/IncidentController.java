package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.application.IncidentCommentService;
import com.nexusops.servicecore.incident.application.IncidentSearchCriteria;
import com.nexusops.servicecore.incident.application.IncidentService;
import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentComment;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "title",
            "priority",
            "status",
            "impact",
            "urgency"
    );

    private final IncidentService incidentService;
    private final IncidentCommentService incidentCommentService;

    public IncidentController(
            IncidentService incidentService,
            IncidentCommentService incidentCommentService
    ) {
        this.incidentService = incidentService;
        this.incidentCommentService = incidentCommentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        Incident incident = incidentService.create(
                request.title(),
                request.description(),
                request.impact(),
                request.urgency()
        );

        URI location = URI.create(
                "/api/v1/incidents/" + incident.getId()
        );

        return ResponseEntity
                .created(location)
                .body(IncidentResponse.from(incident));
    }

    @GetMapping
    public IncidentPageResponse search(
            @RequestParam(required = false)
            IncidentStatus status,

            @RequestParam(required = false)
            Priority priority,

            @RequestParam(required = false)
            Impact impact,

            @RequestParam(required = false)
            Urgency urgency,

            @RequestParam(required = false)
            String teamId,

            @RequestParam(required = false)
            String assigneeId,

            @RequestParam(name = "q", required = false)
            String text,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "createdAt,desc")
            String sort
    ) {
        IncidentSearchCriteria criteria =
                new IncidentSearchCriteria(
                        status,
                        priority,
                        impact,
                        urgency,
                        teamId,
                        assigneeId,
                        text
                );

        Pageable pageable = createPageable(
                page,
                size,
                sort
        );

        Page<Incident> incidents = incidentService.search(
                criteria,
                pageable
        );

        return IncidentPageResponse.from(incidents);
    }

    @GetMapping("/{incidentId}")
    public IncidentResponse getById(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.getById(incidentId);

        return IncidentResponse.from(incident);
    }

    @PatchMapping("/{incidentId}/assessment")
    public IncidentResponse updateAssessment(
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentAssessmentRequest request
    ) {
        Incident incident = incidentService.updateAssessment(
                incidentId,
                request.impact(),
                request.urgency()
        );

        return IncidentResponse.from(incident);
    }

    @PatchMapping("/{incidentId}/assignment/team")
    public IncidentResponse assignToTeam(
            @PathVariable UUID incidentId,
            @Valid @RequestBody AssignIncidentTeamRequest request
    ) {
        Incident incident = incidentService.assignToTeam(
                incidentId,
                request.teamId()
        );

        return IncidentResponse.from(incident);
    }

    @PatchMapping("/{incidentId}/assignment/assignee")
    public IncidentResponse assignToUser(
            @PathVariable UUID incidentId,
            @Valid @RequestBody AssignIncidentUserRequest request
    ) {
        Incident incident = incidentService.assignToUser(
                incidentId,
                request.assigneeId()
        );

        return IncidentResponse.from(incident);
    }

    @DeleteMapping("/{incidentId}/assignment/assignee")
    public IncidentResponse clearAssignee(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.clearAssignee(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    @DeleteMapping("/{incidentId}/assignment/team")
    public IncidentResponse clearTeam(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.clearTeam(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/comments")
    public ResponseEntity<IncidentCommentResponse> addComment(
            @PathVariable UUID incidentId,
            @Valid @RequestBody CreateIncidentCommentRequest request
    ) {
        IncidentComment comment = incidentCommentService.addComment(
                incidentId,
                request.authorId(),
                request.content()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(IncidentCommentResponse.from(comment));
    }

    @GetMapping("/{incidentId}/comments")
    public List<IncidentCommentResponse> listComments(
            @PathVariable UUID incidentId
    ) {
        return incidentCommentService
                .listComments(incidentId)
                .stream()
                .map(IncidentCommentResponse::from)
                .toList();
    }

    @PostMapping("/{incidentId}/start-progress")
    public IncidentResponse startProgress(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.startProgress(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/return-to-open")
    public IncidentResponse returnToOpen(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.returnToOpen(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/resolve")
    public IncidentResponse resolve(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.resolve(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/reopen")
    public IncidentResponse reopen(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.reopen(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/close")
    public IncidentResponse close(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.close(
                incidentId
        );

        return IncidentResponse.from(incident);
    }

    private Pageable createPageable(
            int page,
            int size,
            String sort
    ) {
        if (page < 0) {
            throw badRequest(
                    "page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest(
                    "size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }

        if (sort == null || sort.isBlank()) {
            throw badRequest(
                    "sort must not be blank"
            );
        }

        String[] sortParts = sort.split(",", -1);

        if (sortParts.length > 2) {
            throw badRequest(
                    "sort must use the format field,direction"
            );
        }

        String sortField = sortParts[0].trim();

        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw badRequest(
                    "unsupported sort field: " + sortField
            );
        }

        Sort.Direction direction = Sort.Direction.ASC;

        if (sortParts.length == 2) {
            String directionValue = sortParts[1].trim();

            if (directionValue.isBlank()) {
                throw badRequest(
                        "sort direction must not be blank"
                );
            }

            try {
                direction = Sort.Direction.fromString(
                        directionValue
                );
            } catch (IllegalArgumentException exception) {
                throw badRequest(
                        "sort direction must be asc or desc"
                );
            }
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(
                        direction,
                        sortField
                )
        );
    }

    private ResponseStatusException badRequest(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}