package com.tubereturns.dto;

import com.tubereturns.security.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequestDto(
        @NotBlank String token,
        @StrongPassword String newPassword
) {
}
