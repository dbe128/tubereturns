package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "ticker_symbol", unique = true, nullable = false)
    private String tickerSymbol;

    @Size(max = 500)
    @Column(name = "company_name")
    private String companyName;

    @Size(max = 3)
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "is_unknown", nullable = false)
    private boolean unknown = false;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed = false;

    @Column(name = "failed_resolution_attempts", nullable = false)
    private int failedResolutionAttempts = 0;

    @Column(name = "corporate_action", length = 50)
    private String corporateAction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Stock(String tickerSymbol, String companyName, String currency) {
        this.tickerSymbol = tickerSymbol.toUpperCase();
        this.companyName = companyName;
        this.currency = currency != null ? currency.toUpperCase() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
