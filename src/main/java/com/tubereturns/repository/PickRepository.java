package com.tubereturns.repository;

import com.tubereturns.dto.PickScoringData;
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

@Repository
public interface PickRepository extends JpaRepository<Pick, Long> {

    @Transactional
    void deleteByVideoId(Long videoId);

    @Query("SELECT COUNT(p) FROM Pick p WHERE p.video.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT COUNT(p) FROM Pick p WHERE p.stock.id = :stockId")
    long countByStockId(@Param("stockId") Long stockId);

    @Modifying
    @Transactional
    @Query("UPDATE Pick p SET p.stock = :newStock WHERE p.stock = :oldStock")
    int relinkPicks(@Param("oldStock") Stock oldStock, @Param("newStock") Stock newStock);

    @Query("SELECT p FROM Pick p JOIN FETCH p.video JOIN FETCH p.stock WHERE p.video.channel.id = :channelId AND p.video.excluded = false ORDER BY p.video.publishedAt ASC")
    List<Pick> findPicksByChannelId(@Param("channelId") Long channelId);

    @Query("""
        SELECT new com.tubereturns.dto.PickScoringData(
            v.channel.id, v.publishedAt, s.unknown, p.alpha1m, p.alpha1y, p.alpha3y
        )
        FROM Pick p JOIN p.video v JOIN p.stock s
        WHERE v.excluded = false
        ORDER BY v.channel.id ASC, v.publishedAt ASC
        """)
    List<PickScoringData> findAllPickDataForScoring();

    @Query("""
        SELECT new com.tubereturns.dto.PickScoringData(
            v.channel.id, v.publishedAt, s.unknown, p.alpha1m, p.alpha1y, p.alpha3y
        )
        FROM Pick p JOIN p.video v JOIN p.stock s
        WHERE v.channel.id = :channelId AND v.excluded = false
        ORDER BY v.publishedAt ASC
        """)
    List<PickScoringData> findPickScoringDataForChannel(@Param("channelId") Long channelId);

    @Query("SELECT DISTINCT p.video.channel.id FROM Pick p WHERE p.stock.id = :stockId")
    List<Long> findDistinctChannelIdsByStockId(@Param("stockId") Long stockId);

    @Query("SELECT p FROM Pick p JOIN FETCH p.video v JOIN FETCH p.stock WHERE p.stock.id = :stockId AND v.excluded = false")
    List<Pick> findByStockId(@Param("stockId") Long stockId);

    @Query("""
        SELECT p FROM Pick p
        JOIN FETCH p.video v
        JOIN FETCH p.stock s
        WHERE s.unknown = false
          AND v.excluded = false
          AND (
            (p.return1m IS NULL AND v.publishedAt < :cutoff1m)
            OR (p.return1y IS NULL AND v.publishedAt < :cutoff1y)
            OR (p.return3y IS NULL AND v.publishedAt < :cutoff3y)
          )
        """)
    List<Pick> findPicksNeedingReturnComputation(
        @Param("cutoff1m") Instant cutoff1m,
        @Param("cutoff1y") Instant cutoff1y,
        @Param("cutoff3y") Instant cutoff3y
    );

}
