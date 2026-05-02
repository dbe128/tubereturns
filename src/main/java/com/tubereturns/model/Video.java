package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "video_id", unique = true, nullable = false)
    private String videoId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String title;

    @NotNull
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcript_status", length = 50)
    private TranscriptStatus transcriptStatus = TranscriptStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "extraction_model", length = 100)
    private String extractionModel;

    @Column(nullable = false)
    private boolean excluded = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pick> picks;

    public Video(String videoId, Channel channel, String title, Instant publishedAt) {
        this.videoId = videoId;
        this.channel = channel;
        this.title = title;
        this.publishedAt = publishedAt;
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

    public enum TranscriptStatus {
        PENDING, DOWNLOADING, DOWNLOADED, NO_TRANSCRIPT, FAILED
    }

    public enum ProcessingStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
