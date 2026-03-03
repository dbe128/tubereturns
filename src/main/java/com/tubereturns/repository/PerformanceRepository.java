package com.tubereturns.repository;

import com.tubereturns.model.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    Optional<Performance> findByPickId(Long pickId);

    @Query("SELECT p FROM Performance p WHERE p.pick.tickerSymbol = :ticker ORDER BY p.pick.extractionTimestamp DESC")
    List<Performance> findByTickerSymbolOrderByExtractionTimestampDesc(@Param("ticker") String tickerSymbol);

    @Query("SELECT p FROM Performance p WHERE p.pick.video.channel.id = :channelId ORDER BY p.pick.extractionTimestamp DESC")
    List<Performance> findByChannelIdOrderByExtractionTimestampDesc(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Performance p WHERE p.lastUpdated < :before")
    List<Performance> findPerformanceNeedingUpdate(@Param("before") Instant before);

    @Query("""
        SELECT p FROM Performance p
        WHERE p.pick.video.channel.id = :channelId
        AND p.return30d IS NOT NULL
        ORDER BY p.return30d DESC
        """)
    List<Performance> findBestPerformingPicksByChannel(@Param("channelId") Long channelId);

    @Query("""
        SELECT AVG(p.return30d) FROM Performance p
        WHERE p.pick.video.channel.id = :channelId
        AND p.return30d IS NOT NULL
        """)
    Double findAverageReturn30dByChannel(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Performance p WHERE p.pick.signal = :signal AND p.return30d IS NOT NULL ORDER BY p.return30d DESC")
    List<Performance> findBySignalOrderByReturn30dDesc(@Param("signal") com.tubereturns.model.Pick.Signal signal);
}