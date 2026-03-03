package com.tubereturns.repository;

import com.tubereturns.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByChannelId(String channelId);

    List<Channel> findByIsActiveTrue();

    @Query("SELECT c FROM Channel c WHERE c.isActive = true ORDER BY c.subscriberCount DESC")
    List<Channel> findActiveChannelsOrderBySubscriberCount();

    boolean existsByChannelId(String channelId);
}