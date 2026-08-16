package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.application.IncidentNotFoundException;
import com.nexusops.servicecore.incident.domain.InvalidIncidentTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class IncidentExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException.class)
    public ProblemDetail handleIncidentNotFound(
            IncidentNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );

        problem.setTitle("Incident not found");
        problem.setType(
                URI.create("urn:nexusops:problem:incident-not-found")
        );

        return problem;
    }

    @ExceptionHandler(InvalidIncidentTransitionException.class)
    public ProblemDetail handleInvalidTransition(
            InvalidIncidentTransitionException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Invalid incident transition");
        problem.setType(
                URI.create("urn:nexusops:problem:invalid-incident-transition")
        );

        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.putIfAbsent(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "request validation failed"
        );

        problem.setTitle("Invalid request");
        problem.setType(
                URI.create("urn:nexusops:problem:validation")
        );
        problem.setProperty("errors", errors);

        return problem;
    }
}