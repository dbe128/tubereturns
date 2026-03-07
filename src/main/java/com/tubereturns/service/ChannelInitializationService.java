package com.tubereturns.service;

import com.tubereturns.config.ChannelConfig;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class ChannelInitializationService {

    private final ChannelConfig channelConfig;
    private final ChannelRepository channelRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeChannels() {
        log.info("Initializing predefined channels from configuration");

        if (channelConfig.getChannels() == null || channelConfig.getChannels().isEmpty()) {
            log.warn("No channels defined in configuration");
            return;
        }

        for (ChannelConfig.ChannelDefinition channelDef : channelConfig.getChannels()) {
            if (!channelDef.isEnabled()) {
                log.info("Skipping disabled channel: {}", channelDef.getChannelName());
                continue;
            }

            try {
                Channel existingChannel = channelRepository.findByYoutubeChannelId(channelDef.getYoutubeChannelId()).orElse(null);

                if (existingChannel == null) {
                    Channel newChannel = new Channel(channelDef.getYoutubeChannelId(), channelDef.getChannelName());
                    newChannel.setDescription(channelDef.getDescription());
                    newChannel.setChannelUrl(channelDef.getUrl());

                    channelRepository.save(newChannel);
                    log.info("Created new channel: {} ({})", channelDef.getChannelName(), channelDef.getYoutubeChannelId());
                } else {
                    existingChannel.setChannelName(channelDef.getChannelName());
                    existingChannel.setDescription(channelDef.getDescription());
                    existingChannel.setChannelUrl(channelDef.getUrl());

                    channelRepository.save(existingChannel);
                    log.info("Updated existing channel: {} ({})", channelDef.getChannelName(), channelDef.getYoutubeChannelId());
                }
            } catch (Exception e) {
                log.error("Error initializing channel {}: {}", channelDef.getChannelName(), e.getMessage(), e);
            }
        }

        log.info("Channel initialization completed");
    }
}
