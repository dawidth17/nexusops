package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.application.AssetAssignmentNotFoundException;
import com.nexusops.servicecore.asset.application.AssetIdentifierConflictException;
import com.nexusops.servicecore.asset.application.AssetNotFoundException;
import com.nexusops.servicecore.asset.domain.InvalidAssetOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class AssetExceptionHandler {

    @ExceptionHandler(AssetNotFoundException.class)
    public ProblemDetail handleAssetNotFound(
            AssetNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );

        problem.setTitle("Asset not found");
        problem.setType(
                URI.create(
                        "urn:nexusops:problem:asset-not-found"
                )
        );

        return problem;
    }

    @ExceptionHandler(AssetIdentifierConflictException.class)
    public ProblemDetail handleIdentifierConflict(
            AssetIdentifierConflictException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Asset identifier conflict");
        problem.setType(
                URI.create(
                        "urn:nexusops:problem:asset-identifier-conflict"
                )
        );

        return problem;
    }

    @ExceptionHandler(InvalidAssetOperationException.class)
    public ProblemDetail handleInvalidOperation(
            InvalidAssetOperationException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Invalid asset operation");
        problem.setType(
                URI.create(
                        "urn:nexusops:problem:invalid-asset-operation"
                )
        );

        return problem;
    }

    @ExceptionHandler(AssetAssignmentNotFoundException.class)
    public ProblemDetail handleAssignmentNotFound(
            AssetAssignmentNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Asset assignment state conflict");
        problem.setType(
                URI.create(
                        "urn:nexusops:problem:asset-assignment-conflict"
                )
        );

        return problem;
    }
}