package com.portfolio.knowledge.api.advice;

import com.portfolio.knowledge.domain.DocumentException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class DocumentExceptionHandler {
    @ExceptionHandler(DocumentException.class)
    ProblemDetail documentError(DocumentException exception, HttpServletRequest request) {
        if (exception.status() == 503) {
            org.slf4j.LoggerFactory.getLogger(DocumentExceptionHandler.class)
                    .warn(
                            "event=document_storage_failed errorCode={} causeType={}",
                            exception.code(),
                            exception.getCause() == null
                                    ? "none"
                                    : exception.getCause().getClass().getSimpleName());
        }
        return problem(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class,
        MultipartException.class
    })
    ProblemDetail invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(400, "INVALID_REQUEST", "Request fields or parameters are invalid", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail oversizedUpload(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(413, "UPLOAD_TOO_LARGE", "File size must not exceed 25 MiB", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail persistenceUnavailable(
            DataAccessException exception, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(DocumentExceptionHandler.class)
                .warn(
                        "event=document_persistence_failed errorCode=DOCUMENT_PERSISTENCE_UNAVAILABLE causeType={}",
                        exception.getClass().getSimpleName());
        return problem(
                503,
                "DOCUMENT_PERSISTENCE_UNAVAILABLE",
                "Cannot save document; retry later",
                request);
    }

    private ProblemDetail problem(
            int status, String code, String detail, HttpServletRequest request) {
        var result =
                ProblemDetail.forStatusAndDetail(
                        org.springframework.http.HttpStatusCode.valueOf(status), detail);
        result.setType(
                URI.create("urn:ticket-platform:error:" + code.toLowerCase(java.util.Locale.ROOT)));
        result.setInstance(URI.create(request.getRequestURI()));
        result.setProperty("code", code);
        result.setProperty("correlationId", UUID.randomUUID().toString());
        return result;
    }
}
