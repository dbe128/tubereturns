package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stock_prices",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "price_date"}))
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @NotNull
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @NotNull
    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    @Column(name = "approximated", nullable = false)
    private boolean approximated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockPrice(Stock stock, LocalDate priceDate, Double closePrice) {
        this.stock = stock;
        this.priceDate = priceDate;
        this.closePrice = closePrice;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
