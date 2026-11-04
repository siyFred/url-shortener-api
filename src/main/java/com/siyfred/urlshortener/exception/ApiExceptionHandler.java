package com.siyfred.urlshortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidUrl(InvalidUrlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Invalid URL",
                "message", ex.getMessage()
        ));
    }
}
