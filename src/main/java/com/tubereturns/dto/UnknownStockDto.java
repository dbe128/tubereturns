package com.tubereturns.dto;

public record UnknownStockDto(Long id, String tickerSymbol, String companyName, String currency, String createdAt, long pickCount) {}
