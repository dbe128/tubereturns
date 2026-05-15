package com.tubereturns.service;

import com.tubereturns.dto.PortfolioPricePointDto;
import com.tubereturns.model.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioDataService portfolioDataService;

    public record ChannelReturns(Double return1y, Double return3y, Double return5y) {}

    public List<PortfolioPricePointDto> buildPortfolioPricesForChannel(Channel channel, LocalDate clipFrom) {
        List<PortfolioPricePointDto> raw = portfolioDataService.buildRawSeries(channel);
        if (raw.isEmpty() || clipFrom == null) {
            return raw;
        }
        return clipAndRebase(raw, clipFrom);
    }

    public ChannelReturns computeChannelReturns(Channel channel) {
        List<PortfolioPricePointDto> full = portfolioDataService.buildRawSeries(channel);
        LocalDate now = LocalDate.now();
        return new ChannelReturns(
                returnSince(full, now.minusYears(1)),
                returnSince(full, now.minusYears(3)),
                returnSince(full, now.minusYears(5)));
    }

    @CacheEvict(value = "channelReturns", key = "#channelId")
    public void evictChannelReturns(Long channelId) {}

    @CacheEvict(value = "channelReturns", allEntries = true)
    public void evictAllChannelReturns() {}

    private List<PortfolioPricePointDto> clipAndRebase(List<PortfolioPricePointDto> raw, LocalDate clipFrom) {
        int startIdx = 0;
        while (startIdx < raw.size() && LocalDate.parse(raw.get(startIdx).date()).isBefore(clipFrom)) {
            startIdx++;
        }
        if (startIdx >= raw.size()) {
            return List.of();
        }
        List<PortfolioPricePointDto> clipped = raw.subList(startIdx, raw.size());
        double base = clipped.getFirst().changePercent();
        double baseFactor = 1.0 + base / 100.0;
        if (baseFactor == 0) {
            return clipped;
        }
        return clipped.stream()
                .map(p -> new PortfolioPricePointDto(
                        p.date(),
                        (1.0 + p.changePercent() / 100.0) / baseFactor * 100.0 - 100.0,
                        p.close()))
                .toList();
    }

    private Double returnSince(List<PortfolioPricePointDto> full, LocalDate clipFrom) {
        List<PortfolioPricePointDto> rebased = clipAndRebase(full, clipFrom);
        return rebased.isEmpty() ? null : rebased.getLast().changePercent();
    }
}
