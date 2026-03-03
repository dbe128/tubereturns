package com.tubereturns.repository;

import com.tubereturns.model.Pick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PickRepository extends JpaRepository<Pick, Long> {

    List<Pick> findByTickerSymbolOrderByCreatedAtDesc(String tickerSymbol);

    List<Pick> findByVideoIdOrderByCreatedAtDesc(Long videoId);

    @Query("SELECT p FROM Pick p WHERE p.video.channel.id = :channelId ORDER BY p.createdAt DESC")
    List<Pick> findByChannelIdOrderByCreatedAtDesc(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Pick p WHERE p.video.channel.channelId = :channelId ORDER BY p.createdAt DESC")
    List<Pick> findByYouTubeChannelIdOrderByCreatedAtDesc(@Param("channelId") String channelId);

    @Query("SELECT p FROM Pick p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Pick> findPicksSince(@Param("since") Instant since);

    @Query("SELECT DISTINCT p.tickerSymbol FROM Pick p ORDER BY p.tickerSymbol")
    List<String> findDistinctTickerSymbols();

    @Query("SELECT COUNT(p) FROM Pick p WHERE p.video.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Pick p WHERE p.signal = :signal ORDER BY p.createdAt DESC")
    List<Pick> findBySignalOrderByCreatedAtDesc(@Param("signal") Pick.Signal signal);
}
