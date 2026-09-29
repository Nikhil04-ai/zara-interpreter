package com.zara.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Keeps every /api response in the same {output, error} shape, including
// malformed-request cases (bad JSON, missing body) that Spring would otherwise
// turn into its own differently-shaped default error body.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RunResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new RunResponse("", "Invalid request: " + ex.getMessage()));
    }
}
