package com.tubereturns.service;

import com.tubereturns.config.ChannelConfig;
import com.tubereturns.model.Channel;
import com.tubereturns.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Profile("dev")
@Transactional
public class ChannelInitializationService {

    private final ChannelConfig channelConfig;
    private final ChannelRepository channelRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
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
                Channel existingChannel = channelRepository.findByHandle(channelDef.getHandle()).orElse(null);

                if (existingChannel == null) {
                    Channel newChannel = new Channel(channelDef.getHandle(), channelDef.getChannelName());
                    newChannel.setDescription(channelDef.getDescription());
                    channelRepository.save(newChannel);
                    log.info("Created new channel: {} (@{})", channelDef.getChannelName(), channelDef.getHandle());
                } else {
                    existingChannel.setChannelName(channelDef.getChannelName());
                    existingChannel.setDescription(channelDef.getDescription());
                    channelRepository.save(existingChannel);
                    log.info("Updated existing channel: {} (@{})", channelDef.getChannelName(), channelDef.getHandle());
                }
            } catch (Exception e) {
                log.error("Error initializing channel {}: {}", channelDef.getChannelName(), e.getMessage(), e);
            }
        }

        log.info("Channel initialization completed");
    }
}
