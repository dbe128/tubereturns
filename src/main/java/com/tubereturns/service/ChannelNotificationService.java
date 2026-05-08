package com.tubereturns.service;

import com.tubereturns.model.Channel;
import com.tubereturns.model.ChannelProcessingNotification;
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

    private static final List<Video.ProcessingStatus> INCOMPLETE_STATUSES =
            List.of(Video.ProcessingStatus.PENDING, Video.ProcessingStatus.PROCESSING);

    private final ChannelProcessingNotificationRepository notificationRepository;
    private final VideoRepository videoRepository;
    private final EmailService emailService;

    @Transactional
    public void scheduleNotification(Channel channel, String userEmail) {
        if (notificationRepository.existsByChannelIdAndUserEmailAndSentAtIsNull(channel.getId(), userEmail)) {
            return;
        }
        notificationRepository.save(new ChannelProcessingNotification(channel, userEmail));
        log.info("Scheduled processing notification for channel {} to {}", channel.getHandle(), userEmail);
    }

    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void checkAndSendPendingNotifications() {
        List<ChannelProcessingNotification> pending = notificationRepository.findPending();
        if (pending.isEmpty()) {
            return;
        }
        for (ChannelProcessingNotification notification : pending) {
            Channel channel = notification.getChannel();
            if (channel.getLastProcessedAt() == null
                    || channel.getLastProcessedAt().isBefore(notification.getRequestedAt())) {
                continue;
            }
            if (videoRepository.countByChannelIdAndProcessingStatusIn(channel.getId(), INCOMPLETE_STATUSES) > 0) {
                continue;
            }
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);
            emailService.sendChannelProcessedEmail(
                    notification.getUserEmail(), channel.getChannelName(), channel.getHandle());
        }
    }
}
