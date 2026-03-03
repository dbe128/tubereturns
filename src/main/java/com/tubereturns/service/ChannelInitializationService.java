package com.tubereturns.service;

import com.tubereturns.config.ChannelConfig;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChannelInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(ChannelInitializationService.class);

    private final ChannelConfig channelConfig;
    private final ChannelRepository channelRepository;

    @Autowired
    public ChannelInitializationService(ChannelConfig channelConfig, ChannelRepository channelRepository) {
        this.channelConfig = channelConfig;
        this.channelRepository = channelRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // Run after database initialization
    public void initializeChannels() {
        logger.info("Initializing predefined channels from configuration");

        if (channelConfig.getChannels() == null || channelConfig.getChannels().isEmpty()) {
            logger.warn("No channels defined in configuration");
            return;
        }

        for (ChannelConfig.ChannelDefinition channelDef : channelConfig.getChannels()) {
            if (!channelDef.isEnabled()) {
                logger.info("Skipping disabled channel: {}", channelDef.getChannelName());
                continue;
            }

            try {
                Channel existingChannel = channelRepository.findByChannelId(channelDef.getChannelId()).orElse(null);

                if (existingChannel == null) {
                    Channel newChannel = new Channel(channelDef.getChannelId(), channelDef.getChannelName());
                    newChannel.setDescription(channelDef.getDescription());
                    newChannel.setChannelUrl(channelDef.getUrl());
                    newChannel.setIsActive(true);

                    channelRepository.save(newChannel);
                    logger.info("Created new channel: {} ({})", channelDef.getChannelName(), channelDef.getChannelId());
                } else {
                    existingChannel.setChannelName(channelDef.getChannelName());
                    existingChannel.setDescription(channelDef.getDescription());
                    existingChannel.setChannelUrl(channelDef.getUrl());
                    existingChannel.setIsActive(true);

                    channelRepository.save(existingChannel);
                    logger.info("Updated existing channel: {} ({})", channelDef.getChannelName(), channelDef.getChannelId());
                }
            } catch (Exception e) {
                logger.error("Error initializing channel {}: {}", channelDef.getChannelName(), e.getMessage(), e);
            }
        }

        logger.info("Channel initialization completed");
    }
}