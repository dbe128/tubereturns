package com.tubereturns.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "archivarix_deleted_videos",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "youtube_video_id"}))
public class ArchivarixDeletedVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "youtube_video_id", length = 20, nullable = false)
    private String youtubeVideoId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(name = "upload_date")
    private LocalDate uploadDate;

    @Column(name = "archivarix_status", length = 30, nullable = false)
    private String archivarixStatus;

    @Column(name = "discovered_at", nullable = false, updatable = false)
    private Instant discoveredAt;

    public ArchivarixDeletedVideo(Channel channel, String youtubeVideoId, String title,
                                  LocalDate uploadDate, String archivarixStatus) {
        this.channel = channel;
        this.youtubeVideoId = youtubeVideoId;
        this.title = title;
        this.uploadDate = uploadDate;
        this.archivarixStatus = archivarixStatus;
        this.discoveredAt = Instant.now();
    }
}
