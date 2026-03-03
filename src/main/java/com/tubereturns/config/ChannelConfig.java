package com.tubereturns.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "")
public class ChannelConfig {

    private List<ChannelDefinition> channels;

    public List<ChannelDefinition> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelDefinition> channels) {
        this.channels = channels;
    }

    public static class ChannelDefinition {
        private String channelId;
        private String channelName;
        private String description;
        private boolean enabled = true;
        private String url;

        public String getChannelId() {
            return channelId;
        }

        public void setChannelId(String channelId) {
            this.channelId = channelId;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannelName(String channelName) {
            this.channelName = channelName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}