package com.tubereturns.repository;

import com.tubereturns.model.ChannelSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelSuggestionRepository extends JpaRepository<ChannelSuggestion, String> {
    List<ChannelSuggestion> findByStatusOrderByFirstSuggestedAtDesc(ChannelSuggestion.Status status);
}
