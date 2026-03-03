package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "picks")
public class Pick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @NotBlank
    @Size(max = 10)
    @Setter(AccessLevel.NONE)
    @Column(name = "ticker_symbol", nullable = false)
    private String tickerSymbol;

    @Size(max = 500)
    @Column(name = "company_name")
    private String companyName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Signal signal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Pick(Video video, String tickerSymbol, Signal signal) {
        this.video = video;
        this.tickerSymbol = tickerSymbol.toUpperCase();
        this.signal = signal;
    }

    public void setTickerSymbol(String tickerSymbol) {
        this.tickerSymbol = tickerSymbol != null ? tickerSymbol.toUpperCase() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum Signal {
        BUY, SELL
    }
}
