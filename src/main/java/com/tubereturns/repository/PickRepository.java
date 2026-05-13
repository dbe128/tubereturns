package com.tubereturns.repository;

import com.tubereturns.model.Pick;
import com.tubereturns.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PickRepository extends JpaRepository<Pick, Long> {

    List<Pick> findByStock_TickerSymbolOrderByCreatedAtDesc(String tickerSymbol);

    List<Pick> findByVideoIdOrderByCreatedAtDesc(Long videoId);

    @Transactional
    void deleteByVideoId(Long videoId);

    @Query("SELECT p FROM Pick p WHERE p.video.channel.handle = :handle ORDER BY p.createdAt DESC")
    List<Pick> findByHandleOrderByCreatedAtDesc(@Param("handle") String handle);

    @Query("SELECT p FROM Pick p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Pick> findPicksSince(@Param("since") Instant since);

    @Query("SELECT DISTINCT p.stock.tickerSymbol FROM Pick p ORDER BY p.stock.tickerSymbol")
    List<String> findDistinctTickerSymbols();

    @Query("SELECT COUNT(p) FROM Pick p WHERE p.video.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT COUNT(p) FROM Pick p WHERE p.stock.id = :stockId")
    long countByStockId(@Param("stockId") Long stockId);

    @Modifying
    @Transactional
    @Query("UPDATE Pick p SET p.stock = :newStock WHERE p.stock = :oldStock")
    int relinkPicks(@Param("oldStock") Stock oldStock, @Param("newStock") Stock newStock);

    @Query("SELECT DISTINCT p.stock.tickerSymbol FROM Pick p WHERE p.video.channel.id = :channelId AND p.signal = :signal AND p.video.excluded = false ORDER BY p.stock.tickerSymbol")
    List<String> findDistinctTickersByChannelIdAndSignal(@Param("channelId") Long channelId, @Param("signal") Pick.Signal signal);

    @Query("SELECT p FROM Pick p WHERE p.signal = :signal ORDER BY p.createdAt DESC")
    List<Pick> findBySignalOrderByCreatedAtDesc(@Param("signal") Pick.Signal signal);

    @Query("SELECT MIN(p.video.publishedAt) FROM Pick p WHERE p.video.channel.id = :channelId AND p.signal = 'BUY' AND p.video.excluded = false")
    Optional<Instant> findEarliestBuyPickPublishedAt(@Param("channelId") Long channelId);

    @Query("SELECT MIN(p.video.publishedAt) FROM Pick p WHERE p.video.channel.id = :channelId AND p.signal = 'BUY'")
    Optional<Instant> findEarliestBuyPickPublishedAtAllTime(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Pick p JOIN FETCH p.video JOIN FETCH p.stock WHERE p.video.channel.id = :channelId AND p.signal = 'BUY' AND p.video.excluded = false ORDER BY p.video.publishedAt ASC")
    List<Pick> findBuyPicksByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT p FROM Pick p JOIN FETCH p.video JOIN FETCH p.stock WHERE p.video.channel.id = :channelId AND p.signal = 'SELL' AND p.video.excluded = false ORDER BY p.video.publishedAt ASC")
    List<Pick> findSellPicksByChannelId(@Param("channelId") Long channelId);
}
