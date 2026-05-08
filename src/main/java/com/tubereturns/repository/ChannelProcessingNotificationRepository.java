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

    @Query("SELECT n.channel.handle FROM ChannelProcessingNotification n WHERE n.user.id = :userId AND n.sentAt IS NULL")
    List<String> findPendingHandlesByUserId(Long userId);

    boolean existsByChannelIdAndUserIdAndSentAtIsNull(Long channelId, Long userId);

    void deleteByChannelIdAndUserIdAndSentAtIsNull(Long channelId, Long userId);
}
