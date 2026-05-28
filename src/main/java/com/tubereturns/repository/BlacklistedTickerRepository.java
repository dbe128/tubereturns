package com.tubereturns.repository;

import com.tubereturns.model.BlacklistedTicker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistedTickerRepository extends JpaRepository<BlacklistedTicker, Long> {

    boolean existsByTickerSymbolIgnoreCase(String tickerSymbol);

    Optional<BlacklistedTicker> findByTickerSymbolIgnoreCase(String tickerSymbol);

    List<BlacklistedTicker> findAllByOrderByCreatedAtDesc();
}
