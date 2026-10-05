package com.tubereturns.controller;

import com.tubereturns.dto.BlockedChannelDto;
import com.tubereturns.model.BlockedChannel;
import com.tubereturns.model.ChannelSuggestion;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.ChannelSuggestionRepository;
import com.tubereturns.service.BlockedChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/blocked-channels")
@Tag(name = "Blocked Channels", description = "Channels that can never be added or suggested")
public class BlockedChannelController {

    private final BlockedChannelService blockedChannelService;
    private final ChannelRepository channelRepository;
    private final ChannelSuggestionRepository suggestionRepository;

    public record AddBlockedChannelRequest(String handle, String reason) {}

    @GetMapping
    @Operation(summary = "List all blocked channels")
    public List<BlockedChannelDto> getBlockedChannels() {
        return blockedChannelService.getAll().stream().map(this::toDto).toList();
    }

    @PostMapping
    @Operation(summary = "Block a channel from being added or suggested")
    @Transactional
    public ResponseEntity<Map<String, String>> addBlockedChannel(@RequestBody AddBlockedChannelRequest request) {
        String handle = BlockedChannel.normalizeHandle(request.handle());
        if (handle.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Handle is required"));
        }
        if (channelRepository.existsByHandleIgnoreCase(handle)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Channel '" + handle + "' is still tracked. Delete it first."));
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        BlockedChannel blocked = blockedChannelService.add(handle, reason);
        suggestionRepository.findByHandleIgnoreCase(handle).stream()
                .filter(s -> s.getStatus() == ChannelSuggestion.Status.PENDING)
                .forEach(s -> {
                    s.setStatus(ChannelSuggestion.Status.REJECTED);
                    suggestionRepository.save(s);
                });
        return ResponseEntity.ok(Map.of("message", "Channel '" + blocked.getHandle() + "' blocked"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Unblock a channel")
    public ResponseEntity<Map<String, String>> removeBlockedChannel(@PathVariable Long id) {
        if (!blockedChannelService.remove(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "Channel unblocked"));
    }

    private BlockedChannelDto toDto(BlockedChannel blocked) {
        return new BlockedChannelDto(
                blocked.getId(),
                blocked.getHandle(),
                blocked.getYoutubeChannelId(),
                blocked.getReason(),
                blocked.getCreatedAt().toString());
    }
}
