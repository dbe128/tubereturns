package com.tubereturns.dto;

public record AuthResponseDto(
        String token,
        String email,
        String firstName
) {}
