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
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return buildResponse("Validation Error", "Input validation failed", HttpStatus.BAD_REQUEST, errors);
    }

    /**
     * 2. Handles Missing Resource Errors
     * Result: 404 Not Found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse("Resource Not Found", ex.getMessage(), HttpStatus.NOT_FOUND, null);
    }

    /**
     * 3. Handles Duplicate Resource Errors
     * Result: 409 Conflict
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return buildResponse("Duplicate Resource", ex.getMessage(), HttpStatus.CONFLICT, null);
    }

    /**
     * 4. Handles Invalid Operations (e.g. Editing Archived)
     * Result: 400 Bad Request
     */
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOperation(InvalidOperationException ex) {
        return buildResponse("Invalid Operation", ex.getMessage(), HttpStatus.BAD_REQUEST, null);
    }

    /**
     * 5. Handles Business Logic & Validation Failures
     * Result: 400 Bad Request
     */
    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, Object>> handleBusinessLogicErrors(RuntimeException ex) {
        log.warn("Business logic violation: {}", ex.getMessage());
        return buildResponse("Logic Error", ex.getMessage(), HttpStatus.BAD_REQUEST, null);
    }

    /**
     * 6. Handles Type Mismatch (e.g. String ID instead of Long)
     * Result: 400 Bad Request
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        Object value = ex.getValue();
        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s", value, name, type);

        if ("page".equals(name) || "size".equals(name)) {
            message = "Pagination parameter '" + name + "' must be a valid number.";
        }

        return buildResponse("Bad Request", message, HttpStatus.BAD_REQUEST, null);
    }

    /**
     * 7. Handles No Handler Found (404 for invalid URLs)
     * Note: Requires specific spring config to throw this exception
     */
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(
            org.springframework.web.servlet.NoHandlerFoundException ex) {
        return buildResponse("Not Found", "The requested URL was not found on this server.", HttpStatus.NOT_FOUND,
                null);
    }

    /**
     * 8. Handles Security Violations (Invalid API Key)
     * Result: 401 Unauthorized
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        log.error("Security violation: {}", ex.getMessage());
        return buildResponse("Unauthorized", ex.getMessage(), HttpStatus.UNAUTHORIZED, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleReadable(HttpMessageNotReadableException ex) {
        log.error("Message not readable: ", ex);
        return buildResponse("Bad Request", "Malformed JSON request or invalid data format", HttpStatus.BAD_REQUEST,
                null);
    }

    /**
     * 9. The "Safety Net" - Handles any other unexpected RuntimeExceptions
     * Result: 500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRemainingRuntimeExceptions(RuntimeException ex) {
        log.error("Unexpected error occurred: ", ex);
        return buildResponse("Internal Server Error", "An unexpected error occurred on our end.",
                HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    // Helper method for clean, consistent JSON responses
    private ResponseEntity<Map<String, Object>> buildResponse(String error, String message, HttpStatus status,
            Map<String, String> details) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        response.put("message", message);
        response.put("status", status.value());
        if (details != null && !details.isEmpty()) {
            response.put("details", details);
        }
        return new ResponseEntity<>(response, status);
    }
}