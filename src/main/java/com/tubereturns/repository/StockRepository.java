package com.tubereturns.repository;

import com.tubereturns.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByTickerSymbol(String tickerSymbol);

    @Query("SELECT s FROM Stock s WHERE s.unknown = true AND s.reviewed = false ORDER BY s.companyName ASC")
    List<Stock> findUnknownUnreviewed();
}
