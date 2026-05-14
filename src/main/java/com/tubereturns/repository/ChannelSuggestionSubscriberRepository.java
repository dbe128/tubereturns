package com.tubereturns.repository;

import com.tubereturns.model.ChannelSuggestionSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelSuggestionSubscriberRepository extends JpaRepository<ChannelSuggestionSubscriber, Long> {
    boolean existsByHandleAndUserId(String handle, Long userId);
    java.util.Optional<ChannelSuggestionSubscriber> findByHandleAndUserId(String handle, Long userId);
    List<ChannelSuggestionSubscriber> findByHandle(String handle);
    List<ChannelSuggestionSubscriber> findByUserId(Long userId);
    long countByHandle(String handle);
    void deleteByHandleAndUserId(String handle, Long userId);
}
