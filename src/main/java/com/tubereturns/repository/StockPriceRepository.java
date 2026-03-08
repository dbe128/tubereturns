package com.tubereturns.repository;

import com.tubereturns.model.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    boolean existsByStockIdAndPriceDate(Long stockId, LocalDate priceDate);
}
