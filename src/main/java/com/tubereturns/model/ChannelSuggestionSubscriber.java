package com.tubereturns.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "channel_suggestion_subscribers")
public class ChannelSuggestionSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String handle;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notify_on_complete", nullable = false)
    private boolean notifyOnComplete = false;

    @Column(name = "subscribed_at", nullable = false, updatable = false)
    private Instant subscribedAt;

    public ChannelSuggestionSubscriber(String handle, Long userId, boolean notifyOnComplete) {
        this.handle = handle;
        this.userId = userId;
        this.notifyOnComplete = notifyOnComplete;
    }

    @PrePersist
    protected void onCreate() {
        if (subscribedAt == null) {
            subscribedAt = Instant.now();
        }
    }
}
