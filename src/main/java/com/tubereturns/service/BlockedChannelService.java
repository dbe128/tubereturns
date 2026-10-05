package com.tubereturns.service;

import com.tubereturns.dto.ChannelSearchResultDto;
import com.tubereturns.model.BlockedChannel;
import com.tubereturns.repository.BlockedChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BlockedChannelService {

    public static final String BLOCKED_MESSAGE = "This channel cannot be added.";

    private final BlockedChannelRepository repository;
    private final YouTubeApiService youTubeApiService;

    public boolean isBlocked(String handle, String youtubeChannelId) {
        if (isListed(handle, youtubeChannelId)) {
            return true;
        }
        if (!repository.existsByYoutubeChannelIdIsNotNull()) {
            return false;
        }
        ChannelSearchResultDto resolved = youTubeApiService.resolveChannelByHandle(BlockedChannel.normalizeHandle(handle));
        return resolved != null && isListed(resolved.handle(), resolved.channelId());
    }

    public boolean isListed(String handle, String youtubeChannelId) {
        if (repository.existsByHandle(BlockedChannel.normalizeHandle(handle))) {
            return true;
        }
        return youtubeChannelId != null && !youtubeChannelId.isBlank()
                && repository.existsByYoutubeChannelId(youtubeChannelId);
    }

    public List<BlockedChannel> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public BlockedChannel add(String handle, String reason) {
        String normalized = BlockedChannel.normalizeHandle(handle);
        return repository.findByHandle(normalized)
                .orElseGet(() -> {
                    ChannelSearchResultDto resolved = youTubeApiService.resolveChannelByHandle(normalized);
                    String youtubeChannelId = resolved != null ? resolved.channelId() : null;
                    log.info("Blocked channel @{} (youtubeChannelId: {}, reason: {})", normalized, youtubeChannelId, reason);
                    return repository.save(new BlockedChannel(normalized, youtubeChannelId, reason));
                });
    }

    public boolean remove(Long id) {
        return repository.findById(id)
                .map(blocked -> {
                    repository.delete(blocked);
                    log.info("Unblocked channel @{}", blocked.getHandle());
                    return true;
                })
                .orElse(false);
    }
}
