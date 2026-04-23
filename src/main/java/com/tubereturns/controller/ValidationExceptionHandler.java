package com.tubereturns.controller;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.equalsIgnoreCase("must not be blank"))
                .collect(Collectors.joining(". "));
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please fill in all required fields"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
