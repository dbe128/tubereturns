package com.tubereturns.repository;

import com.tubereturns.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByVideoId(String videoId);

    @Query("SELECT v FROM Video v JOIN FETCH v.channel WHERE v.videoId = :videoId")
    Optional<Video> findByVideoIdWithChannel(@Param("videoId") String videoId);

    List<Video> findByChannelIdOrderByPublishedAtDesc(Long channelId);

    Page<Video> findByChannelIdOrderByPublishedAtDesc(Long channelId, Pageable pageable);

    @Query("""
        SELECT v FROM Video v
        WHERE v.channel.id = :channelId
          AND (:showExcluded = true OR v.excluded = false)
          AND (:hideUnprocessed = false OR (
                  v.transcriptStatus NOT IN ('PENDING', 'DOWNLOADING')
                  AND NOT (v.transcriptStatus = 'DOWNLOADED'
                           AND v.extractionStatus <> 'EXTRACTED')))
          AND (:transcriptStatus IS NULL OR v.transcriptStatus = :transcriptStatus)
          AND (:extractionStatus IS NULL OR v.extractionStatus = :extractionStatus)
          AND (:requirePicks = false
               OR EXISTS (SELECT p FROM Pick p WHERE p.video = v))
        """)
    Page<Video> findByChannelIdWithFilters(
        @Param("channelId") Long channelId,
        @Param("showExcluded") boolean showExcluded,
        @Param("hideUnprocessed") boolean hideUnprocessed,
        @Param("transcriptStatus") Video.TranscriptStatus transcriptStatus,
        @Param("extractionStatus") Video.ExtractionStatus extractionStatus,
        @Param("requirePicks") boolean requirePicks,
        Pageable pageable);

    @Query("SELECT v FROM Video v WHERE v.transcriptStatus = :status AND v.excluded = false ORDER BY v.publishedAt ASC LIMIT :limit")
    List<Video> findByTranscriptStatus(@Param("status") Video.TranscriptStatus status, @Param("limit") int limit);

    @Query("SELECT v FROM Video v WHERE v.transcriptStatus = :status AND v.excluded = false ORDER BY v.publishedAt ASC")
    List<Video> findAllByTranscriptStatus(@Param("status") Video.TranscriptStatus status);

    @Query("SELECT v FROM Video v WHERE v.transcriptStatus IN :statuses AND v.excluded = false ORDER BY v.publishedAt ASC")
    List<Video> findAllByTranscriptStatusIn(@Param("statuses") List<Video.TranscriptStatus> statuses);

    @Query("SELECT v FROM Video v WHERE v.extractionStatus = :status")
    List<Video> findByExtractionStatus(@Param("status") Video.ExtractionStatus status);

    @Query("SELECT v FROM Video v WHERE v.publishedAt >= :since ORDER BY v.publishedAt DESC")
    List<Video> findVideosPublishedSince(@Param("since") Instant since);

    @Query("SELECT v FROM Video v JOIN FETCH v.channel WHERE v.transcriptStatus = 'DOWNLOADED' AND v.extractionStatus IN ('PENDING', 'FAILED') AND v.excluded = false ORDER BY v.channel.createdAt ASC, v.publishedAt ASC")
    List<Video> findAllVideosReadyForProcessing();

    boolean existsByVideoId(String videoId);

    @Modifying
    @Query("UPDATE Video v SET v.transcriptStatus = 'PENDING' WHERE v.transcriptStatus = 'DOWNLOADING'")
    int resetStaleTranscriptStatuses();

    @Modifying
    @Query("UPDATE Video v SET v.extractionStatus = 'PENDING' WHERE v.extractionStatus = 'EXTRACTING'")
    int resetStaleExtractionStatuses();

    @Query("SELECT COUNT(v) FROM Video v WHERE v.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT v.channel.id, COUNT(v) FROM Video v GROUP BY v.channel.id")
    List<Object[]> countAllGroupedByChannelId();

    @Query("SELECT v.channel.id, COUNT(v) FROM Video v WHERE v.extractionStatus = 'EXTRACTED' GROUP BY v.channel.id")
    List<Object[]> countProcessedGroupedByChannelId();

    @Query("SELECT COUNT(v) FROM Video v WHERE v.channel.id = :channelId AND v.extractionStatus = :status")
    long countByChannelIdAndExtractionStatus(@Param("channelId") Long channelId, @Param("status") Video.ExtractionStatus status);

    @Query("SELECT COUNT(v) FROM Video v WHERE v.channel.id = :channelId AND v.extractionStatus = 'EXTRACTED'")
    long countProcessedByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT COUNT(v) FROM Video v WHERE v.channel.id = :channelId AND v.extractionStatus IN :statuses AND v.excluded = false")
    long countByChannelIdAndExtractionStatusIn(@Param("channelId") Long channelId, @Param("statuses") java.util.Collection<Video.ExtractionStatus> statuses);

    @Query("SELECT COUNT(v) FROM Video v WHERE v.channel.id = :channelId AND v.transcriptStatus IN :statuses AND v.excluded = false")
    long countByChannelIdAndTranscriptStatusIn(@Param("channelId") Long channelId, @Param("statuses") java.util.Collection<Video.TranscriptStatus> statuses);
}
