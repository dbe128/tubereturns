package com.tubereturns.controller;

import com.tubereturns.dto.ChannelSuggestionDto;
import com.tubereturns.model.ChannelSuggestion;
import com.tubereturns.model.ChannelSuggestionSubscriber;
import com.tubereturns.repository.ChannelRepository;
import com.tubereturns.repository.ChannelSuggestionRepository;
import com.tubereturns.repository.ChannelSuggestionSubscriberRepository;
import com.tubereturns.repository.UserRepository;
import com.tubereturns.service.ChannelNotificationService;
import com.tubereturns.service.PipelineSchedulerService;
import com.tubereturns.service.YouTubeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.tubereturns.dto.MyChannelSuggestionDto;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "Channel Suggestions", description = "User channel suggestions pending admin review")
public class ChannelSuggestionController {

    private final ChannelSuggestionRepository suggestionRepository;
    private final ChannelSuggestionSubscriberRepository subscriberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final ChannelNotificationService channelNotificationService;
    private final YouTubeDiscoveryService discoveryService;
    private final PipelineSchedulerService scheduler;

    @PostMapping("/api/channel-suggestions")
    @Operation(summary = "Suggest a channel for tracking")
    public ResponseEntity<Map<String, String>> suggest(
            @RequestParam String handle,
            @RequestParam String channelName,
            @RequestParam(required = false, defaultValue = "") String channelUrl,
            @RequestParam(required = false, defaultValue = "") String thumbnailUrl,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false) Long subscriberCount,
            @RequestParam(defaultValue = "false") boolean notifyOnComplete,
            Authentication authentication) {
        if (channelRepository.findByHandle(handle).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "This channel is already being tracked."));
        }
        var user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user != null && subscriberRepository.existsByHandleAndUserId(handle, user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "You already suggested this channel."));
        }
        ChannelSuggestion suggestion = suggestionRepository.findById(handle).orElseGet(() -> {
            byte[] thumbnailData = downloadBytes(thumbnailUrl);
            return suggestionRepository.save(new ChannelSuggestion(
                    handle, channelName, channelUrl,
                    thumbnailUrl.isBlank() ? null : thumbnailUrl,
                    thumbnailData, thumbnailData != null ? "image/jpeg" : null,
                    description.isBlank() ? null : description, subscriberCount));
        });
        if (suggestion.getStatus() != ChannelSuggestion.Status.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("message", "This channel was already reviewed."));
        }
        if (user != null) {
            subscriberRepository.save(new ChannelSuggestionSubscriber(handle, user.getId(), notifyOnComplete));
        }
        return ResponseEntity.ok(Map.of("message", "Channel suggested! We'll review it soon."));
    }

    @GetMapping("/api/channel-suggestions/my")
    @Operation(summary = "List the current user's channel suggestions")
    public ResponseEntity<List<MyChannelSuggestionDto>> getMySuggestions(Authentication authentication) {
        if (authentication == null) { return ResponseEntity.ok(List.of()); }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> {
                    List<MyChannelSuggestionDto> result = subscriberRepository.findByUserId(user.getId()).stream()
                            .map(sub -> suggestionRepository.findById(sub.getHandle())
                                    .map(s -> toMyDto(s, sub)).orElse(null))
                            .filter(Objects::nonNull)
                            .toList();
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.ok(List.of()));
    }

    @PatchMapping("/api/channel-suggestions/{handle}/notify")
    @Operation(summary = "Update notification preference for a suggestion")
    public ResponseEntity<Void> updateNotify(
            @PathVariable String handle,
            @RequestParam boolean enabled,
            Authentication authentication) {
        if (authentication == null) { return ResponseEntity.status(401).build(); }
        return userRepository.findByEmail(authentication.getName())
                .flatMap(user -> subscriberRepository.findByHandleAndUserId(handle, user.getId()))
                .map(sub -> {
                    sub.setNotifyOnComplete(enabled);
                    subscriberRepository.save(sub);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/channel-suggestions/{handle}")
    @Transactional
    @Operation(summary = "Remove the current user's channel suggestion")
    public ResponseEntity<Void> deleteMySuggestion(
            @PathVariable String handle,
            Authentication authentication) {
        if (authentication == null) { return ResponseEntity.status(401).build(); }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> {
                    subscriberRepository.deleteByHandleAndUserId(handle, user.getId());
                    if (subscriberRepository.countByHandle(handle) == 0) {
                        suggestionRepository.deleteById(handle);
                    }
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/channel-suggestions/{handle}/thumbnail")
    @Operation(summary = "Get suggested channel thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable String handle) {
        return suggestionRepository.findById(handle)
                .filter(s -> s.getThumbnailData() != null)
                .map(s -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                s.getThumbnailContentType() != null ? s.getThumbnailContentType() : "image/jpeg"))
                        .body(s.getThumbnailData()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/admin/channel-suggestions")
    @Operation(summary = "List pending channel suggestions")
    public List<ChannelSuggestionDto> getPending() {
        return suggestionRepository.findByStatusOrderByFirstSuggestedAtDesc(ChannelSuggestion.Status.PENDING)
                .stream().map(this::toDto)
                .sorted((a, b) -> Long.compare(b.suggestionCount(), a.suggestionCount()))
                .toList();
    }

    @PostMapping("/api/admin/channel-suggestions/{handle}/add")
    @Operation(summary = "Approve and add a suggested channel")
    public ResponseEntity<Void> addSuggestion(@PathVariable String handle) {
        return suggestionRepository.findById(handle).map(s -> {
            var channel = discoveryService.createOrUpdateChannel(s.getHandle(), s.getChannelName(),
                    s.getChannelUrl(), s.getThumbnailUrl(), s.getDescription(), s.getSubscriberCount());
            subscriberRepository.findByHandle(handle).stream()
                    .filter(ChannelSuggestionSubscriber::isNotifyOnComplete)
                    .forEach(sub -> userRepository.findById(sub.getUserId())
                            .ifPresent(user -> channelNotificationService.scheduleNotification(channel, user)));
            s.setStatus(ChannelSuggestion.Status.ADDED);
            suggestionRepository.save(s);
            scheduler.triggerDiscovery();
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/admin/channel-suggestions/{handle}/reject")
    @Operation(summary = "Reject a suggested channel")
    public ResponseEntity<Void> rejectSuggestion(@PathVariable String handle) {
        return suggestionRepository.findById(handle).map(s -> {
            s.setStatus(ChannelSuggestion.Status.REJECTED);
            suggestionRepository.save(s);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private ChannelSuggestionDto toDto(ChannelSuggestion s) {
        long count = subscriberRepository.countByHandle(s.getHandle());
        return new ChannelSuggestionDto(
                s.getHandle(),
                s.getChannelName(),
                s.getChannelUrl(),
                s.getDescription(),
                s.getSubscriberCount(),
                count,
                s.getFirstSuggestedAt(),
                s.getStatus().name()
        );
    }

    private MyChannelSuggestionDto toMyDto(ChannelSuggestion s, ChannelSuggestionSubscriber sub) {
        return new MyChannelSuggestionDto(
                s.getHandle(), s.getChannelName(), s.getChannelUrl(), s.getDescription(),
                s.getSubscriberCount(), sub.isNotifyOnComplete(), sub.getSubscribedAt(), s.getStatus().name());
    }

    private byte[] downloadBytes(String url) {
        if (url == null || url.isBlank()) { return null; }
        try {
            return URI.create(url).toURL().openStream().readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to download suggestion thumbnail: {}", e.getMessage());
            return null;
        }
    }
}
