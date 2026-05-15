package com.tubereturns.service;

import com.tubereturns.model.Channel;
import com.tubereturns.model.ChannelProcessingNotification;
import com.tubereturns.model.User;
import com.tubereturns.model.Video;
import com.tubereturns.repository.ChannelProcessingNotificationRepository;
import com.tubereturns.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChannelNotificationService {

    private static final List<Video.TranscriptStatus> INCOMPLETE_TRANSCRIPT_STATUSES =
            List.of(Video.TranscriptStatus.PENDING, Video.TranscriptStatus.DOWNLOADING);

    private static final List<Video.ExtractionStatus> INCOMPLETE_EXTRACTION_STATUSES =
            List.of(Video.ExtractionStatus.PENDING, Video.ExtractionStatus.EXTRACTING);

    private final ChannelProcessingNotificationRepository notificationRepository;
    private final VideoRepository videoRepository;
    private final EmailService emailService;

    @Transactional
    public void cancelNotification(Channel channel, User user) {
        notificationRepository.deleteByChannelIdAndUserIdAndSentAtIsNull(channel.getId(), user.getId());
        log.info("Cancelled processing notification for channel {} for {}", channel.getHandle(), user.getEmail());
    }

    @Transactional
    public void scheduleNotification(Channel channel, User user) {
        if (notificationRepository.existsByChannelIdAndUserIdAndSentAtIsNull(channel.getId(), user.getId())) {
            return;
        }
        notificationRepository.save(new ChannelProcessingNotification(channel, user));
        log.info("Scheduled processing notification for channel {} to {}", channel.getHandle(), user.getEmail());
    }

    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void checkAndSendPendingNotifications() {
        List<ChannelProcessingNotification> pending = notificationRepository.findPending();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Checking {} pending notification(s)", pending.size());
        for (ChannelProcessingNotification notification : pending) {
            Channel channel = notification.getChannel();
            if (!channel.isDiscoveryComplete()) {
                log.info("Skipping notification for {} → discovery not complete", channel.getHandle());
                continue;
            }
            long pendingTranscripts = videoRepository.countByChannelIdAndTranscriptStatusIn(channel.getId(), INCOMPLETE_TRANSCRIPT_STATUSES);
            if (pendingTranscripts > 0) {
                log.info("Skipping notification for {} → {} transcript(s) still pending", channel.getHandle(), pendingTranscripts);
                continue;
            }
            long pendingExtractions = videoRepository.countByChannelIdAndExtractionStatusIn(channel.getId(), INCOMPLETE_EXTRACTION_STATUSES);
            if (pendingExtractions > 0) {
                log.info("Skipping notification for {} → {} extraction(s) still pending", channel.getHandle(), pendingExtractions);
                continue;
            }
            log.info("Sending processing-complete notification for channel {} to {}", channel.getHandle(), notification.getUser().getEmail());
            boolean sent = emailService.sendChannelProcessedEmail(
                    notification.getUser().getEmail(), channel.getChannelName(), channel.getHandle());
            if (sent) {
                notification.setSentAt(Instant.now());
                notificationRepository.save(notification);
            }
        }
    }
}
