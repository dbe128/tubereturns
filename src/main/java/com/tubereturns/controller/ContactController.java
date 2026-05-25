package com.tubereturns.controller;

import com.tubereturns.dto.ContactRequestDto;
import com.tubereturns.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;

    @Operation(summary = "Send a contact / feedback message")
    @PostMapping
    public ResponseEntity<Void> contact(@Valid @RequestBody ContactRequestDto req) {
        emailService.sendContactEmail(req.name(), req.email(), req.message());
        return ResponseEntity.ok().build();
    }
}
