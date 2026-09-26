package com.portfolio.ticket.api.advice;

import com.portfolio.ticket.application.TicketForbiddenException;
import com.portfolio.ticket.application.TicketNotFoundException;
import com.portfolio.ticket.domain.ClosedTicketMutationException;
import com.portfolio.ticket.domain.InvalidTicketTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TicketExceptionHandler {

    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket was not found", request);
    }

    @ExceptionHandler(TicketForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(HttpServletRequest request) {
        return response(
                HttpStatus.FORBIDDEN,
                "TICKET_FORBIDDEN",
                "Caller is not allowed to perform this operation",
                request);
    }

    @ExceptionHandler(InvalidTicketTransitionException.class)
    ResponseEntity<ProblemDetail> invalidTransition(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "TICKET_INVALID_TRANSITION",
                "The requested ticket status transition is not allowed",
                request);
    }

    @ExceptionHandler(ClosedTicketMutationException.class)
    ResponseEntity<ProblemDetail> closedTicket(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "TICKET_CLOSED",
                "Closed ticket must be reopened before it can be changed",
                request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> badRequest(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:ticket-platform:error:" + code.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return ResponseEntity.status(status).body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        var supplied = request.getHeader("X-Correlation-ID");
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
