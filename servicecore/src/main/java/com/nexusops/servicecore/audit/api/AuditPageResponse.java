package com.nexusops.servicecore.audit.api;

import com.nexusops.servicecore.audit.domain.AuditEntry;
import org.springframework.data.domain.Page;

import java.util.List;

public record AuditPageResponse(
        List<AuditResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static AuditPageResponse from(
            Page<AuditEntry> entries
    ) {
        return new AuditPageResponse(
                entries.getContent()
                        .stream()
                        .map(AuditResponse::from)
                        .toList(),
                entries.getNumber(),
                entries.getSize(),
                entries.getTotalElements(),
                entries.getTotalPages()
        );
    }
}
