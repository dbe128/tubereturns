package com.tubereturns.repository;

import com.tubereturns.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    boolean existsByCurrencyIdAndRateDate(Long currencyId, LocalDate rateDate);

    long countByCurrencyId(Long currencyId);

    List<ExchangeRate> findByCurrencyIdAndRateDateBetweenOrderByRateDateAsc(Long currencyId, LocalDate from, LocalDate to);
}
