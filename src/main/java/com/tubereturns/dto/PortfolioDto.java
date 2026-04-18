package com.tubereturns.dto;

import java.util.List;

public record PortfolioDto(String channelId, String name, String startDate, List<String> tickers) {}
