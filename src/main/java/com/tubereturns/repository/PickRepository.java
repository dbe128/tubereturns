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

    List<Pick> findByStock_TickerSymbolOrderByCreatedAtDesc(String tickerSymbol);

    List<Pick> findByVideoIdOrderByCreatedAtDesc(Long videoId);

    @Query("SELECT p FROM Pick p WHERE p.video.channel.youtubeChannelId = :youtubeChannelId ORDER BY p.createdAt DESC")
    List<Pick> findByYouTubeChannelIdOrderByCreatedAtDesc(@Param("youtubeChannelId") String youtubeChannelId);

    @Query("SELECT p FROM Pick p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Pick> findPicksSince(@Param("since") Instant since);

    @Query("SELECT DISTINCT p.stock.tickerSymbol FROM Pick p ORDER BY p.stock.tickerSymbol")
    List<String> findDistinctTickerSymbols();

    @Query("SELECT COUNT(p) FROM Pick p WHERE p.video.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Pick p WHERE p.signal = :signal ORDER BY p.createdAt DESC")
    List<Pick> findBySignalOrderByCreatedAtDesc(@Param("signal") Pick.Signal signal);
}
