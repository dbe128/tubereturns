package com.tubereturns.dto;

import com.tubereturns.security.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequestDto(
        @NotBlank String firstName,
        @NotBlank @Email String email,
        @StrongPassword String password
) {}
