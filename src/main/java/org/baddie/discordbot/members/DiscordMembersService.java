package org.baddie.discordbot.members;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DiscordMembersService {
    private final RestTemplate restTemplate;
    private final String guildId;
    private final HttpEntity<Void> request;
    private final LongSupplier nanoTime;
    private List<String> cachedUsernames;
    private Map<String, String> cachedDisplayNames;
    private long cacheLoadedAt;
    private static final long CACHE_TTL_NANOS = Duration.ofMinutes(10).toNanos();

    @Autowired
    public DiscordMembersService(RestTemplateBuilder builder,
                                 @Value("${service.discord.token}") String token,
                                 @Value("${service.discord.guildId:}") String guildId) {
        this(builder.connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30)).build(), token, guildId, System::nanoTime);
    }

    DiscordMembersService(RestTemplate restTemplate, String token, String guildId, LongSupplier nanoTime) {
        this.restTemplate = restTemplate;
        this.nanoTime = nanoTime;
        this.guildId = guildId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bot " + token);
        this.request = new HttpEntity<>(headers);
    }

    public List<MemberResponse> getMembers() {
        return loadMembers().stream().map(DiscordMembersService::filter)
                .filter(member -> !member.roles().isEmpty()).toList();
    }

    public synchronized boolean usernameExists(String username) {
        refreshCacheIfNeeded();
        return cachedUsernames.contains(username);
    }

    public synchronized EventProfile getEventProfile(String username) {
        refreshCacheIfNeeded();
        String displayName = cachedDisplayNames.get(username);
        return new EventProfile(displayName != null, displayName);
    }

    private void refreshCacheIfNeeded() {
        if (cachedUsernames == null || nanoTime.getAsLong() - cacheLoadedAt >= CACHE_TTL_NANOS) {
            List<DiscordMember> members = loadMembers();
            List<String> refreshed = members.stream().map(member -> member.user().username()).toList();
            Map<String, String> displayNames = new HashMap<>();
            members.forEach(member -> displayNames.put(member.user().username(), displayName(member)));
            Map<String, String> refreshedDisplayNames = Map.copyOf(displayNames);
            cachedUsernames = List.copyOf(refreshed);
            cachedDisplayNames = refreshedDisplayNames;
            cacheLoadedAt = nanoTime.getAsLong();
        }
    }

    private List<DiscordMember> loadMembers() {
        if (!guildId.matches("[0-9]{17,20}")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure service.discord.guildId");
        }
        List<DiscordMember> result = new ArrayList<>();
        String after = "0";
        try {
            while (true) {
                DiscordMember[] page = restTemplate.exchange(
                        "https://discord.com/api/v10/guilds/{guildId}/members?limit=1000&after={after}",
                        HttpMethod.GET, request, DiscordMember[].class, guildId, after).getBody();
                if (page == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty Discord response");
                }
                for (DiscordMember member : page) {
                    if (member == null || member.user() == null || member.user().username() == null
                            || member.user().id() == null || !member.user().id().matches("[0-9]{17,20}")) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Discord member response");
                    }
                    result.add(member);
                }
                if (page.length < 1000) {
                    return List.copyOf(result);
                }
                String next = page[page.length - 1].user().id();
                if (new java.math.BigInteger(next).compareTo(new java.math.BigInteger(after)) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Discord pagination did not advance");
                }
                after = next;
            }
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to load Discord members", error);
        }
    }

    static MemberResponse filter(DiscordMember member) {
        List<String> roles = Arrays.stream(DiscordMemberRole.values())
                .filter(role -> member.roles() != null && member.roles().contains(role.getId()))
                .map(DiscordMemberRole::getDisplayName).toList();
        return new MemberResponse(displayName(member), roles);
    }

    private static String displayName(DiscordMember member) {
        String nick = member.nick();
        if (nick == null || nick.isBlank()) {
            nick = member.user().global_name();
        }
        if (nick == null || nick.isBlank()) {
            nick = member.user().username();
        }
        return nick;
    }

    public record EventProfile(boolean member, String displayName) {}
    public record MemberResponse(String nick, List<String> roles) {}
    public record DiscordMember(String nick, List<String> roles, DiscordUser user) {}
    public record DiscordUser(String id, String username, String global_name) {}
}
