package com.kieru.backend.exception;

import com.kieru.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. VALIDATION ERRORS (@Valid fails)
     * Scenario: User sends blank content or bad email.
     * Action: Return 400 Bad Request with specific field errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        // Extract specific field errors (e.g. "email": "must be valid")
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message(errors.toString()) // Returns "{email=invalid, content=empty}"
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 2. BUSINESS LOGIC ERRORS (Service throws RuntimeException)
     * Scenario: Rate limit reached, Secret not found (if strictly thrown), etc.
     * Action: Return 400 Bad Request (or 429 if we create a specific exception later).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeErrors(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        // Logic: You might want to switch status code based on message
        // For now, we treat business logic failures as 400 (Client Error)
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (ex.getMessage().contains("Limit")) {
            status = HttpStatus.TOO_MANY_REQUESTS; // 429
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(response, status);
    }

    /**
     * 3. CATCH-ALL (The "Oops" Handler)
     * Scenario: Database crash, NullPointer, OutOfMemory.
     * Action: Return 500 Internal Server Error (Don't leak stack trace to user).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericErrors(
            Exception ex,
            HttpServletRequest request
    ) {
        // In Prod: LOG THIS ERROR so you can fix it!
        ex.printStackTrace();

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("Something went wrong. Please try again later.")
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}