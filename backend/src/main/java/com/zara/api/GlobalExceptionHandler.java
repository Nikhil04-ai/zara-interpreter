package com.zara.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Keeps every /api response in the same {output, error} shape, with the correct
// HTTP status and no leakage of internal details (class/method names, raw
// framework exception text). Known "this is the client's fault" cases get their
// own handler with a clean message and the right 4xx status; anything
// unanticipated falls through to a generic 500 -- the real exception is only
// logged server-side, never echoed back to the caller.
//
// Deliberately NOT scoped to a specific controller (e.g. via
// @RestControllerAdvice(assignableTypes = ...)): that was tried and reverted
// because Spring resolves HttpRequestMethodNotSupportedException (wrong HTTP
// method on a real route) in a way that scoped advice doesn't catch either,
// which silently lost our {output,error} shape for that case too. Instead,
// NoResourceFoundException -- the actual culprit, thrown for unmatched static
// paths like "/" and "/favicon.ico" -- gets its own explicit handler here so
// the generic Exception.class catch-all below never sees it.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RunResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new RunResponse("", "HTTP method '" + ex.getMethod() + "' is not supported on this endpoint"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RunResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RunResponse("", "Request body is missing or is not valid JSON. Expected: {\"code\": \"<zara source>\"}"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RunResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing an /api request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RunResponse("", "Internal server error"));
    }
}
