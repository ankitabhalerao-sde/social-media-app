package com.meet5.userservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<APIError> handleUserNotFoundException(UserNotFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new APIError(HttpStatus.NOT_FOUND.value(),
                        "USER_NOT_FOUND",
                        e.getMessage(),
                        request.getRequestURI(),
                        Instant.now())
        );
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<APIError> handleDuplicateUsernameException(DuplicateUsernameException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new APIError(HttpStatus.CONFLICT.value(),
                        "USERNAME_ALREADY_EXISTS",
                        e.getMessage(),
                        request.getRequestURI(),
                        Instant.now())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIError> handleValidationFailed(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        LOGGER.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new APIError(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_FAILED",
                        fieldErrors.toString(),
                        request.getRequestURI(),
                        Instant.now()
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIError> handleMissingBodyException(HttpMessageNotReadableException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new APIError(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_FAILED",
                        "HTTP MESSAGE MISSING",
                        request.getRequestURI(),
                        Instant.now()
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        LOGGER.error("Method argument type mismatch: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new APIError(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                e.getMessage(),
                request.getRequestURI(),
                Instant.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIError> handleUnexpected(Exception e, HttpServletRequest request) {
        LOGGER.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new APIError(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        request.getRequestURI(),
                        Instant.now()
                )
        );
    }
}
