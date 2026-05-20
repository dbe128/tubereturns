package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "channels")
@SQLRestriction("deleted_at IS NULL")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "handle", unique = true, nullable = false)
    private String handle;

    @NotBlank
    @Size(max = 500)
    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "thumbnail_data")
    private byte[] thumbnailData;

    @Column(name = "thumbnail_content_type", length = 50)
    private String thumbnailContentType;

    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "subscriber_count")
    private Long subscriberCount;

    @Column(name = "discovery_complete", nullable = false)
    private boolean discoveryComplete = false;

    @Column(name = "approval_source", length = 20)
    private String approvalSource;

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Video> videos;

    public Channel(String handle, String channelName) {
        this.handle = handle;
        this.channelName = channelName;
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
}
