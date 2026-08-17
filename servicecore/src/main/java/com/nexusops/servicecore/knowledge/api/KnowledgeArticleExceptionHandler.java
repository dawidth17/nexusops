package com.nexusops.servicecore.knowledge.api;

import com.nexusops.servicecore.knowledge.application.KnowledgeArticleNotFoundException;
import com.nexusops.servicecore.knowledge.domain.InvalidKnowledgeArticleOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class KnowledgeArticleExceptionHandler {

    @ExceptionHandler(
            KnowledgeArticleNotFoundException.class
    )
    public ProblemDetail handleNotFound(
            KnowledgeArticleNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        exception.getMessage()
                );

        problem.setTitle(
                "Knowledge article not found"
        );

        problem.setType(
                URI.create(
                        "urn:nexusops:problem:knowledge-article-not-found"
                )
        );

        return problem;
    }

    @ExceptionHandler(
            InvalidKnowledgeArticleOperationException.class
    )
    public ProblemDetail handleInvalidOperation(
            InvalidKnowledgeArticleOperationException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );

        problem.setTitle(
                "Invalid knowledge article operation"
        );

        problem.setType(
                URI.create(
                        "urn:nexusops:problem:invalid-knowledge-article-operation"
                )
        );

        return problem;
    }
}
