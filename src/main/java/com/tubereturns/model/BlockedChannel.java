package com.tubereturns.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "blocked_channels")
public class BlockedChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "handle", unique = true, nullable = false)
    private String handle;

    @Size(max = 64)
    @Column(name = "youtube_channel_id")
    private String youtubeChannelId;

    @Size(max = 500)
    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BlockedChannel(String handle, String youtubeChannelId, String reason) {
        this.handle = normalizeHandle(handle);
        this.youtubeChannelId = youtubeChannelId;
        this.reason = reason;
    }

    public static String normalizeHandle(String handle) {
        if (handle == null) {
            return "";
        }
        String stripped = handle.strip();
        if (stripped.startsWith("@")) {
            stripped = stripped.substring(1);
        }
        return stripped.toLowerCase();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
