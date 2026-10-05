package com.tubereturns.repository;

import com.tubereturns.model.BlockedChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockedChannelRepository extends JpaRepository<BlockedChannel, Long> {

    boolean existsByHandle(String handle);

    boolean existsByYoutubeChannelId(String youtubeChannelId);

    boolean existsByYoutubeChannelIdIsNotNull();

    Optional<BlockedChannel> findByHandle(String handle);

    List<BlockedChannel> findAllByOrderByCreatedAtDesc();
}
