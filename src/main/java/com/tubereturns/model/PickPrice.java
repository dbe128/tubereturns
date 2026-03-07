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
@Table(name = "pick_prices")
public class PickPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pick_id", nullable = false, unique = true)
    private Pick pick;

    @NotNull
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @NotNull
    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PickPrice(Pick pick, LocalDate priceDate, Double closePrice) {
        this.pick = pick;
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
