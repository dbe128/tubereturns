package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "performance")
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pick_id", nullable = false, unique = true)
    private Pick pick;

    @Column(name = "start_price", precision = 12, scale = 4)
    private BigDecimal startPrice;

    @Column(name = "current_price", precision = 12, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "return_1d", precision = 8, scale = 4)
    private BigDecimal return1d;

    @Column(name = "return_7d", precision = 8, scale = 4)
    private BigDecimal return7d;

    @Column(name = "return_30d", precision = 8, scale = 4)
    private BigDecimal return30d;

    @Column(name = "return_90d", precision = 8, scale = 4)
    private BigDecimal return90d;

    @Column(name = "return_1y", precision = 8, scale = 4)
    private BigDecimal return1y;

    @Column(name = "return_ytd", precision = 8, scale = 4)
    private BigDecimal returnYtd;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Performance() {}

    public Performance(Pick pick) {
        this.pick = pick;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pick getPick() {
        return pick;
    }

    public void setPick(Pick pick) {
        this.pick = pick;
    }

    public BigDecimal getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(BigDecimal startPrice) {
        this.startPrice = startPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getReturn1d() {
        return return1d;
    }

    public void setReturn1d(BigDecimal return1d) {
        this.return1d = return1d;
    }

    public BigDecimal getReturn7d() {
        return return7d;
    }

    public void setReturn7d(BigDecimal return7d) {
        this.return7d = return7d;
    }

    public BigDecimal getReturn30d() {
        return return30d;
    }

    public void setReturn30d(BigDecimal return30d) {
        this.return30d = return30d;
    }

    public BigDecimal getReturn90d() {
        return return90d;
    }

    public void setReturn90d(BigDecimal return90d) {
        this.return90d = return90d;
    }

    public BigDecimal getReturn1y() {
        return return1y;
    }

    public void setReturn1y(BigDecimal return1y) {
        this.return1y = return1y;
    }

    public BigDecimal getReturnYtd() {
        return returnYtd;
    }

    public void setReturnYtd(BigDecimal returnYtd) {
        this.returnYtd = returnYtd;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}