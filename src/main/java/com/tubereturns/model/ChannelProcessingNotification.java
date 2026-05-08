package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "channel_processing_notifications")
public class ChannelProcessingNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @NotNull
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @NotNull
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public ChannelProcessingNotification(Channel channel, String userEmail) {
        this.channel = channel;
        this.userEmail = userEmail;
        this.requestedAt = Instant.now();
    }
}
