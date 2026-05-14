package com.tubereturns.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "channel_suggestions")
public class ChannelSuggestion implements Persistable<String> {

    @Transient
    private boolean isNew = true;

    @Id
    private String handle;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "channel_url")
    private String channelUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "thumbnail_data")
    private byte[] thumbnailData;

    @Column(name = "thumbnail_content_type", length = 50)
    private String thumbnailContentType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "subscriber_count")
    private Long subscriberCount;

    @Column(name = "first_suggested_at", nullable = false, updatable = false)
    private Instant firstSuggestedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    public enum Status { PENDING, REJECTED, ADDED }

    public ChannelSuggestion(String handle, String channelName, String channelUrl, String thumbnailUrl,
                             byte[] thumbnailData, String thumbnailContentType,
                             String description, Long subscriberCount) {
        this.handle = handle;
        this.channelName = channelName;
        this.channelUrl = channelUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.thumbnailData = thumbnailData;
        this.thumbnailContentType = thumbnailContentType;
        this.description = description;
        this.subscriberCount = subscriberCount;
    }

    @Override
    public String getId() { return handle; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    void markNotNew() { this.isNew = false; }

    @PrePersist
    protected void onCreate() {
        if (firstSuggestedAt == null) {
            firstSuggestedAt = Instant.now();
        }
    }
}
