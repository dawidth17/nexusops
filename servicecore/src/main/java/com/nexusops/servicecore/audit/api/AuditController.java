package com.nexusops.servicecore.audit.api;

import com.nexusops.servicecore.audit.application.AuditService;
import com.nexusops.servicecore.audit.domain.AuditEntityType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditService auditService;

    public AuditController(
            AuditService auditService
    ) {
        this.auditService = auditService;
    }

    @GetMapping("/{entityType}/{entityId}")
    public AuditPageResponse getHistory(
            @PathVariable AuditEntityType entityType,
            @PathVariable UUID entityId,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100"
            );
        }

        return AuditPageResponse.from(
                auditService.getHistory(
                        entityType,
                        entityId,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "occurredAt"
                                )
                        )
                )
        );
    }
}