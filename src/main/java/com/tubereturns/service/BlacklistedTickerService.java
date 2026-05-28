package com.tubereturns.service;

import com.tubereturns.model.BlacklistedTicker;
import com.tubereturns.repository.BlacklistedTickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BlacklistedTickerService {

    private final BlacklistedTickerRepository repository;

    public boolean isBlacklisted(String ticker) {
        return repository.existsByTickerSymbolIgnoreCase(ticker);
    }

    public List<BlacklistedTicker> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public BlacklistedTicker add(String ticker, String reason) {
        String normalized = ticker.strip().toUpperCase();
        return repository.findByTickerSymbolIgnoreCase(normalized)
                .orElseGet(() -> {
                    BlacklistedTicker bt = new BlacklistedTicker(normalized, reason);
                    log.info("Added '{}' to blacklist (reason: {})", normalized, reason);
                    return repository.save(bt);
                });
    }

    public boolean remove(String ticker) {
        return repository.findByTickerSymbolIgnoreCase(ticker)
                .map(bt -> {
                    repository.delete(bt);
                    log.info("Removed '{}' from blacklist", bt.getTickerSymbol());
                    return true;
                })
                .orElse(false);
    }
}
