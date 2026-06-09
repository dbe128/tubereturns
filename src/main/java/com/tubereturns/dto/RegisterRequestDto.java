package com.tubereturns.dto;

import com.tubereturns.security.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Email @Size(max = 200) String email,
        @StrongPassword String password
) {}
