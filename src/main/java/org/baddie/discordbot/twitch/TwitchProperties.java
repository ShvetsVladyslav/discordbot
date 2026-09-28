package org.baddie.discordbot.twitch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "service.twitch")
public class TwitchProperties {

    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private boolean notifyOnStartup;
    private long checkIntervalMs = 60000;
    private List<Streamer> streamers = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public boolean isNotifyOnStartup() {
        return notifyOnStartup;
    }

    public void setNotifyOnStartup(boolean notifyOnStartup) {
        this.notifyOnStartup = notifyOnStartup;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public List<Streamer> getStreamers() {
        return streamers;
    }

    public void setStreamers(List<Streamer> streamers) {
        this.streamers = streamers;
    }

    public static class Streamer {

        private String login;
        private String discordChannelId;
        private String messageTemplate = "@everyone {displayName} начал стрим!";
        private boolean embedEnabled = true;

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getDiscordChannelId() {
            return discordChannelId;
        }

        public void setDiscordChannelId(String discordChannelId) {
            this.discordChannelId = discordChannelId;
        }

        public String getMessageTemplate() {
            return messageTemplate;
        }

        public void setMessageTemplate(String messageTemplate) {
            this.messageTemplate = messageTemplate;
        }

        public boolean isEmbedEnabled() {
            return embedEnabled;
        }

        public void setEmbedEnabled(boolean embedEnabled) {
            this.embedEnabled = embedEnabled;
        }
    }
}