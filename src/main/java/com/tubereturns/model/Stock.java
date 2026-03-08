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
    @Size(max = 10)
    @Column(name = "ticker_symbol", unique = true, nullable = false)
    private String tickerSymbol;

    @Size(max = 500)
    @Column(name = "company_name")
    private String companyName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Stock(String tickerSymbol, String companyName) {
        this.tickerSymbol = tickerSymbol.toUpperCase();
        this.companyName = companyName;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
