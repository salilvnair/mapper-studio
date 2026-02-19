package com.salilvnair.mapperstudio.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? "Request could not be processed."
                : ex.getReason();
        return ResponseEntity.status(status).body(errorBody(status, message, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        StringBuilder sb = new StringBuilder("Invalid request.");
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null) {
            sb.append(" ").append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage());
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(status, sb.toString(), request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(status, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Unexpected error while handling request."
                : ex.getMessage();
        return ResponseEntity.status(status).body(errorBody(status, message, request.getRequestURI()));
    }

    private Map<String, Object> errorBody(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("status", status.value());
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }
}
