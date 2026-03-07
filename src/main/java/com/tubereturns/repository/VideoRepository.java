package com.tubereturns.repository;

import com.tubereturns.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByVideoId(String videoId);

    List<Video> findByChannelIdOrderByPublishedAtDesc(Long channelId);

    Page<Video> findByChannelIdOrderByPublishedAtDesc(Long channelId, Pageable pageable);

    @Query("SELECT v FROM Video v WHERE v.transcriptStatus = :status LIMIT 1")
    List<Video> findByTranscriptStatus(@Param("status") Video.TranscriptStatus status);

    @Query("SELECT v FROM Video v WHERE v.processingStatus = :status")
    List<Video> findByProcessingStatus(@Param("status") Video.ProcessingStatus status);

    @Query("SELECT v FROM Video v WHERE v.publishedAt >= :since ORDER BY v.publishedAt DESC")
    List<Video> findVideosPublishedSince(@Param("since") Instant since);

    @Query("SELECT v FROM Video v WHERE v.transcriptStatus = 'DOWNLOADED' AND v.processingStatus = 'PENDING' ORDER BY v.publishedAt ASC")
    List<Video> findVideosReadyForProcessing();

    boolean existsByVideoId(String videoId);

    @Query("SELECT COUNT(v) FROM Video v WHERE v.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);
}