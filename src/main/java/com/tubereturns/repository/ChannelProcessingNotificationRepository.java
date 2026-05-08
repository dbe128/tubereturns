package com.tubereturns.repository;

import com.tubereturns.model.ChannelProcessingNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelProcessingNotificationRepository extends JpaRepository<ChannelProcessingNotification, Long> {

    @Query("SELECT n FROM ChannelProcessingNotification n JOIN FETCH n.channel JOIN FETCH n.user WHERE n.sentAt IS NULL")
    List<ChannelProcessingNotification> findPending();

    boolean existsByChannelIdAndUserIdAndSentAtIsNull(Long channelId, Long userId);
}
