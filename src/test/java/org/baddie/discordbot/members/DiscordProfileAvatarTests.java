package org.baddie.discordbot.members;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordProfileAvatarTests {
    private static final String GUILD = "123456789012345678";
    private static final String USER = "123456789012345679";
    private static final String MEMBERS = "https://discord.com/api/v10/guilds/" + GUILD
            + "/members?limit=1000&after=0";
    private static final String USER_HASH = "a".repeat(32);
    private static final String GUILD_HASH = "b".repeat(32);
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(client);
    private final AtomicLong now = new AtomicLong();
    private final DiscordMembersService service = new DiscordMembersService(client, "token", GUILD, now::get);

    private String member(String userHash, String guildHash, String discriminator) {
        return "[{\"nick\":\"это sunny\",\"avatar\":" + json(guildHash)
                + ",\"roles\":[\"998350106023698432\"],\"joined_at\":\"2020-06-01T00:00:00Z\","
                + "\"user\":{\"id\":\"" + USER + "\",\"username\":\"luckerfourleaf\","
                + "\"avatar\":" + json(userHash) + ",\"discriminator\":" + json(discriminator) + "}}]";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test void prefersGuildAvatarAndPreservesProfileFields() {
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(USER_HASH, GUILD_HASH, "0"), MediaType.APPLICATION_JSON));
        var profile = service.getEventProfile("luckerfourleaf");
        assertThat(profile.avatarUrl()).isEqualTo("https://cdn.discordapp.com/guilds/" + GUILD
                + "/users/" + USER + "/avatars/" + GUILD_HASH + ".png?size=512");
        assertThat(profile.displayName()).isEqualTo("это sunny");
        assertThat(profile.roles()).containsExactly("998350106023698432");
        assertThat(profile.joined_at()).isEqualTo("2020-06-01T00:00:00Z");
        assertThat(service.usernameExists("luckerfourleaf")).isTrue();
        server.verify();
    }

    @Test void fallsBackToGlobalAvatar() {
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(USER_HASH, null, "0"), MediaType.APPLICATION_JSON));
        assertThat(service.getEventProfile("luckerfourleaf").avatarUrl())
                .isEqualTo("https://cdn.discordapp.com/avatars/" + USER + "/" + USER_HASH + ".png?size=512");
        server.verify();
    }

    @Test void retainsAnimationForGuildAndGlobalAvatars() {
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(USER_HASH, "a_" + GUILD_HASH, "0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member("a_" + USER_HASH, null, "0"), MediaType.APPLICATION_JSON));
        assertThat(service.getEventProfile("luckerfourleaf").avatarUrl())
                .endsWith("/a_" + GUILD_HASH + ".webp?size=512&animated=true");
        now.set(Duration.ofMinutes(10).toNanos());
        assertThat(service.getEventProfile("luckerfourleaf").avatarUrl())
                .isEqualTo("https://cdn.discordapp.com/avatars/" + USER + "/a_" + USER_HASH + ".webp?size=512&animated=true");
        server.verify();
    }

    @Test void returnsDefaultAvatarForModernAndLegacyUsers() {
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(null, null, "0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(null, null, "1234"), MediaType.APPLICATION_JSON));
        assertThat(service.getEventProfile("luckerfourleaf").avatarUrl())
                .isEqualTo("https://cdn.discordapp.com/embed/avatars/0.png?size=512");
        now.set(Duration.ofMinutes(10).toNanos());
        assertThat(service.getEventProfile("luckerfourleaf").avatarUrl())
                .isEqualTo("https://cdn.discordapp.com/embed/avatars/4.png?size=512");
        server.verify();
    }

    @Test void cachesAvatarWithProfileAndRefreshesItAfterTenMinutes() {
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(USER_HASH, null, null), MediaType.APPLICATION_JSON));
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess(member(GUILD_HASH, null, null), MediaType.APPLICATION_JSON));
        var first = service.getEventProfile("luckerfourleaf");
        now.set(Duration.ofMinutes(10).toNanos() - 1);
        assertThat(service.getEventProfile("luckerfourleaf")).isEqualTo(first);
        now.incrementAndGet();
        assertThat(service.getEventProfile("luckerfourleaf").avatarUrl()).contains(GUILD_HASH);
        server.verify();
    }

    @Test void unknownMemberHasNoAvatar() {
        server.expect(requestTo(MEMBERS)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        var profile = service.getEventProfile("unknown");
        assertThat(profile.member()).isFalse();
        assertThat(profile.avatarUrl()).isNull();
        server.verify();
    }
}
