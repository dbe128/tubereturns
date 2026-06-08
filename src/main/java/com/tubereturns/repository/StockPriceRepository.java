package com.tubereturns.repository;

import com.tubereturns.model.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO stock_prices (stock_id, price_date, close_price, created_at) VALUES (:stockId, :priceDate, :closePrice, NOW()) ON CONFLICT DO NOTHING", nativeQuery = true)
    void upsert(@Param("stockId") Long stockId, @Param("priceDate") LocalDate priceDate, @Param("closePrice") double closePrice);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO stock_prices (stock_id, price_date, close_price, approximated, created_at) VALUES (:stockId, :priceDate, :closePrice, TRUE, NOW()) ON CONFLICT DO NOTHING", nativeQuery = true)
    void upsertApproximated(@Param("stockId") Long stockId, @Param("priceDate") LocalDate priceDate, @Param("closePrice") double closePrice);

    java.util.Optional<StockPrice> findFirstByStockIdOrderByPriceDateAsc(Long stockId);

    List<StockPrice> findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(Long stockId, LocalDate from, LocalDate to);

    List<StockPrice> findByStockIdAndPriceDateGreaterThanEqualOrderByPriceDateAsc(Long stockId, LocalDate from);

    @Transactional
    void deleteAllByStockId(Long stockId);

    @Query("SELECT MAX(sp.priceDate) FROM StockPrice sp")
    Optional<LocalDate> findMaxPriceDate();
}
