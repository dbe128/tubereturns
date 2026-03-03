package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "channel_id", unique = true, nullable = false)
    private String channelId;

    @NotBlank
    @Size(max = 500)
    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "subscriber_count")
    private Long subscriberCount;

    @Column(name = "video_count")
    private Integer videoCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "channel_url")
    private String channelUrl;

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Video> videos;

    public Channel(String channelId, String channelName) {
        this.channelId = channelId;
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
