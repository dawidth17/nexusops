package com.nexusops.servicecore.sla.api;

import com.nexusops.servicecore.sla.application.IncidentSlaNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class SlaExceptionHandler {

    @ExceptionHandler(IncidentSlaNotFoundException.class)
    public ProblemDetail handleIncidentSlaNotFound(
            IncidentSlaNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );

        problem.setTitle("Incident SLA not found");

        problem.setType(
                URI.create(
                        "urn:nexusops:problem:incident-sla-not-found"
                )
        );

        return problem;
    }
}
