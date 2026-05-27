package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Signal signal;

    @Column(name = "return_1m")
    private Double return1m;

    @Column(name = "return_1y")
    private Double return1y;

    @Column(name = "return_3y")
    private Double return3y;

    @Column(name = "alpha_1m")
    private Double alpha1m;

    @Column(name = "alpha_1y")
    private Double alpha1y;

    @Column(name = "alpha_3y")
    private Double alpha3y;

    @Column(name = "approximated_prices", nullable = false)
    private boolean approximatedPrices = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Pick(Video video, Stock stock, Signal signal) {
        this.video = video;
        this.stock = stock;
        this.signal = signal;
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
        BUY
    }
}
