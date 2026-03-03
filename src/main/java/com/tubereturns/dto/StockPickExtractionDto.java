package com.tubereturns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StockPickExtractionDto(
    @NotBlank @Size(max = 255) String videoId,
    @NotEmpty @Valid List<PickExtractionDto> extractions
) {

    public record PickExtractionDto(
        @NotBlank @Size(max = 10) String tickerSymbol,
        @Size(max = 500) String companyName,
        @NotBlank String signal
    ) {}
}