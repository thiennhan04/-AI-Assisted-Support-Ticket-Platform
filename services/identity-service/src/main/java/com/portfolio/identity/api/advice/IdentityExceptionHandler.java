package com.portfolio.identity.api.advice;

import com.portfolio.identity.application.AuthInvalidCredentialsException;
import com.portfolio.identity.application.LoginRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(AuthInvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        problem(
                                request,
                                HttpStatus.UNAUTHORIZED,
                                "AUTH_INVALID_CREDENTIALS",
                                "Authentication failed"));
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(
            LoginRateLimitExceededException exception, HttpServletRequest request) {
        var retryAfter = Math.max(1, exception.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfter))
                .body(
                        problem(
                                request,
                                HttpStatus.TOO_MANY_REQUESTS,
                                "AUTH_RATE_LIMITED",
                                "Too many login attempts"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        var problem =
                problem(
                        request,
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "Request validation failed");
        var violations =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        Map.of(
                                                "field",
                                                error.getField(),
                                                "message",
                                                error.getDefaultMessage()))
                        .toList();
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        problem(
                                request,
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_FAILED",
                                "Malformed request body"));
    }

    private ProblemDetail problem(
            HttpServletRequest request, HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(
                URI.create("urn:ticket-platform:error:" + code.toLowerCase(java.util.Locale.ROOT)));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private String correlationId(HttpServletRequest request) {
        var supplied = request.getHeader("X-Correlation-ID");
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
