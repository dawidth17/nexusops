package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Urgency;
import jakarta.validation.constraints.NotNull;

public record UpdateIncidentAssessmentRequest(

        @NotNull
        Impact impact,

        @NotNull
        Urgency urgency

) {
}