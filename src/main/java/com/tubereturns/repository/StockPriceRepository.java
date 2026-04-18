package com.tubereturns.repository;

import com.tubereturns.model.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    boolean existsByStockIdAndPriceDate(Long stockId, LocalDate priceDate);

    List<StockPrice> findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(Long stockId, LocalDate from, LocalDate to);

    List<StockPrice> findByStockIdAndPriceDateGreaterThanEqualOrderByPriceDateAsc(Long stockId, LocalDate from);
}
