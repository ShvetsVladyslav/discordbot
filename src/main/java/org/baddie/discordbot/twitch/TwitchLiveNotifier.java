package org.baddie.discordbot.twitch;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.awt.Color;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "service.twitch", name = "enabled", havingValue = "true")
public class TwitchLiveNotifier {

    private static final String TWITCH_TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final String TWITCH_STREAMS_URL = "https://api.twitch.tv/helix/streams?user_login={login}";

    private final JDA jda;
    private final TwitchProperties properties;
    private final RestClient restClient;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;
    private final Map<String, Boolean> liveStatusByLogin = new HashMap<>();
    private final Map<String, Boolean> initializedByLogin = new HashMap<>();

    public TwitchLiveNotifier(JDA jda, TwitchProperties properties, RestClient.Builder restClientBuilder) {
        this.jda = jda;
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Scheduled(fixedDelayString = "${service.twitch.check-interval-ms:60000}")
    public void checkStream() {
        if (!hasText(properties.getClientId()) || !hasText(properties.getClientSecret())) {
            log.warn("Twitch notifier is enabled, but client id or client secret is missing.");
            return;
        }

        if (properties.getStreamers() == null || properties.getStreamers().isEmpty()) {
            log.warn("Twitch notifier is enabled, but streamers list is empty.");
            return;
        }

        for (TwitchProperties.Streamer streamer : properties.getStreamers()) {
            checkStreamer(streamer);
        }
    }

    private void checkStreamer(TwitchProperties.Streamer streamer) {
        if (!isConfigured(streamer)) {
            log.warn("Twitch streamer config is incomplete. Login and Discord channel id are required.");
            return;
        }

        String login = streamer.getLogin().trim().toLowerCase();

        try {
            TwitchStream stream = getCurrentStream(login);
            boolean isLive = stream != null;
            boolean initialized = initializedByLogin.getOrDefault(login, false);
            boolean wasLive = liveStatusByLogin.getOrDefault(login, false);

            if (!initialized) {
                initializedByLogin.put(login, true);
                liveStatusByLogin.put(login, isLive);

                if (isLive && properties.isNotifyOnStartup()) {
                    sendLiveMessage(streamer, stream);
                }
                return;
            }

            if (isLive && !wasLive) {
                sendLiveMessage(streamer, stream);
            }

            liveStatusByLogin.put(login, isLive);
        } catch (Exception e) {
            log.warn("Could not check Twitch stream status for {}", login, e);
        }
    }

    private boolean isConfigured(TwitchProperties.Streamer streamer) {
        return streamer != null
                && hasText(streamer.getLogin())
                && hasText(streamer.getDiscordChannelId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private TwitchStream getCurrentStream(String login) {
        TwitchStreamsResponse response = restClient.get()
                .uri(TWITCH_STREAMS_URL, login)
                .header("Client-Id", properties.getClientId())
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(TwitchStreamsResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return null;
        }

        TwitchStream stream = response.data().get(0);
        return "live".equalsIgnoreCase(stream.type()) ? stream : null;
    }

    private String getAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }

        var body = new LinkedMultiValueMap<String, String>();
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("grant_type", "client_credentials");

        TwitchTokenResponse response = restClient.post()
                .uri(TWITCH_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TwitchTokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Twitch did not return an access token");
        }

        accessToken = response.accessToken();
        tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
        return accessToken;
    }

    private void sendLiveMessage(TwitchProperties.Streamer streamer, TwitchStream stream) {
        TextChannel channel = jda.getTextChannelById(streamer.getDiscordChannelId());
        if (channel == null) {
            log.warn("Discord channel {} was not found for Twitch notifications.", streamer.getDiscordChannelId());
            return;
        }

        Member selfMember = channel.getGuild().getSelfMember();
        if (!selfMember.hasPermission(channel, Permission.MESSAGE_SEND)) {
            log.warn("Bot cannot send Twitch notifications to channel {}. Missing permission: Send Messages.", streamer.getDiscordChannelId());
            return;
        }

        String login = safe(streamer.getLogin()).trim();
        String url = "https://www.twitch.tv/" + login;
        String content = formatTemplate(streamer.getMessageTemplate(), login, url, stream);

        if (!streamer.isEmbedEnabled() || !selfMember.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
            String fallback = content + "\n" + safe(stream.title()) + "\n" + url;
            channel.sendMessage(fallback).queue(
                    success -> log.info("Twitch live notification sent for {}", login),
                    error -> log.warn("Could not send Twitch live notification", error)
            );
            return;
        }

        String thumbnailUrl = safe(stream.thumbnailUrl())
                .replace("{width}", "1280")
                .replace("{height}", "720");

        var embed = new EmbedBuilder()
                .setColor(new Color(100, 65, 165))
                .setAuthor(safe(stream.userName()) + " is now live on Twitch!", url)
                .setTitle(safe(stream.title()), url)
                .addField("Game", safe(stream.gameName()), true)
                .addField("Viewers", String.valueOf(stream.viewerCount()), true)
                .setImage(thumbnailUrl)
                .setFooter("twitch.tv/" + login)
                .build();

        var message = new MessageCreateBuilder()
                .setContent(content)
                .setEmbeds(embed)
                .addActionRow(Button.link(url, "Смотреть стрим"))
                .build();

        channel.sendMessage(message).queue(
                success -> log.info("Twitch live notification sent for {}", login),
                error -> log.warn("Could not send Twitch live notification", error)
        );
    }

    private String formatTemplate(String template, String login, String url, TwitchStream stream) {
        return fixEncoding(template)
                .replace("{displayName}", safe(stream.userName()))
                .replace("{login}", login)
                .replace("{title}", safe(stream.title()))
                .replace("{game}", safe(stream.gameName()))
                .replace("{viewers}", String.valueOf(stream.viewerCount()))
                .replace("{url}", url)
                .replace("\\n", "\n");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String fixEncoding(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains("Рџ") || value.contains("Рќ") || value.contains("РЅ") || value.contains("С‚")) {
            return new String(value.getBytes(Charset.forName("windows-1251")), StandardCharsets.UTF_8);
        }
        if (value.contains("Ð") || value.contains("Ñ")) {
            return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }
        return value;
    }

    private record TwitchTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType
    ) {
    }

    private record TwitchStreamsResponse(List<TwitchStream> data) {
    }

    private record TwitchStream(
            String id,
            @com.fasterxml.jackson.annotation.JsonProperty("user_login") String userLogin,
            @com.fasterxml.jackson.annotation.JsonProperty("user_name") String userName,
            @com.fasterxml.jackson.annotation.JsonProperty("game_name") String gameName,
            String type,
            String title,
            @com.fasterxml.jackson.annotation.JsonProperty("viewer_count") int viewerCount,
            @com.fasterxml.jackson.annotation.JsonProperty("started_at") String startedAt,
            @com.fasterxml.jackson.annotation.JsonProperty("thumbnail_url") String thumbnailUrl
    ) {
    }
}
