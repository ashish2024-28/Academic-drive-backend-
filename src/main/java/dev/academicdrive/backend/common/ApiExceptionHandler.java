package dev.academicdrive.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        return problem(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Request validation failed.");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.");
    }

    @ExceptionHandler(OAuth2AuthorizationException.class)
    ResponseEntity<ProblemDetail> handleDriveAuthorization(OAuth2AuthorizationException exception) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Google Drive authorization is no longer valid. Reconnect Google Drive.");
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ProblemDetail> handleGoogleApiFailure(RestClientResponseException exception) {
        logger.warn("Upstream Google API request failed with status {}.", exception.getStatusCode().value());
        return problem(HttpStatus.BAD_GATEWAY, "Google API request failed. Check the Drive connection and retry.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            org.springframework.http.HttpStatusCode status, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                status, detail == null || detail.isBlank() ? "Request could not be completed." : detail);
        return ResponseEntity.status(status).body(body);
    }
}
