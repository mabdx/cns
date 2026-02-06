package com.example.cns.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. Handles Validation Errors (e.g., @NotBlank, @NotNull in DTOs)
     * Result: 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * 2. Handles Missing Resource Errors
     * Result: 404 Not Found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse("Resource Not Found", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    /**
     * 3. Handles Business Logic & Validation Failures (Missing tags, Inactive templates)
     * Result: 400 Bad Request (This fixes your 500 issue!)
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBusinessLogicErrors(RuntimeException ex) {
        log.warn("Business logic violation: {}", ex.getMessage());
        return buildResponse("Logic Error", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * 4. Handles Security Violations (Invalid API Key)
     * Result: 401 Unauthorized
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException ex) {
        log.error("Security violation: {}", ex.getMessage());
        return buildResponse("Unauthorized", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * 5. The "Safety Net" - Handles any other unexpected RuntimeExceptions
     * Result: 500 Internal Server Error (but with a clean message)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRemainingRuntimeExceptions(RuntimeException ex) {
        log.error("Unexpected error occurred: ", ex);
        return buildResponse("Internal Server Error", "An unexpected error occurred on our end.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleIllegal(RuntimeException ex) {
        log.error("Unexpected error occurred: ", ex);
        return buildResponse("Illegal Argument", "Illegal Arguments in API", HttpStatus.BAD_REQUEST);
    }

    // Helper method for clean, consistent JSON responses
    private ResponseEntity<Map<String, String>> buildResponse(String error, String message, HttpStatus status) {
        Map<String, String> response = new HashMap<>();
        response.put("error", error);
        response.put("message", message);
        response.put("status", String.valueOf(status.value()));
        return new ResponseEntity<>(response, status);
    }
}