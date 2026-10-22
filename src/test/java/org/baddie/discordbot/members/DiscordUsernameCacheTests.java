package org.baddie.discordbot.members;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DiscordUsernameCacheTests {
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(client);
    private final AtomicLong now = new AtomicLong();
    private final DiscordMembersService service = new DiscordMembersService(
            client, "token", "123456789012345678", now::get);
    private static final String FIRST_PAGE = "https://discord.com/api/v10/guilds/123456789012345678/members?limit=1000&after=0";

    private String member(long id, String username) {
        return "{\"user\":{\"id\":\"" + id + "\",\"username\":\"" + username
                + "\"},\"nick\":\"different-nickname\",\"roles\":[]}";
    }

    @Test void cachesUsernamesForExactlyTenMinutesRegardlessOfRole() {
        server.expect(requestTo(FIRST_PAGE)).andExpect(header("Authorization", "Bot token"))
                .andRespond(withSuccess("[" + member(123456789012345679L, "operator") + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FIRST_PAGE))
                .andRespond(withSuccess("[" + member(123456789012345679L, "renamed") + "]", MediaType.APPLICATION_JSON));
        assertThat(service.usernameExists("operator")).isTrue();
        assertThat(service.usernameExists("different-nickname")).isFalse();
        assertThat(service.usernameExists("Operator")).isFalse();
        now.set(Duration.ofMinutes(10).toNanos() - 1);
        assertThat(service.usernameExists("operator")).isTrue();
        now.incrementAndGet();
        assertThat(service.usernameExists("operator")).isFalse();
        assertThat(service.usernameExists("renamed")).isTrue();
        server.verify();
    }

    @Test void cachesAnEmptyGuild() {
        server.expect(requestTo(FIRST_PAGE)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(service.usernameExists("operator")).isFalse();
        assertThat(service.usernameExists("someone_else")).isFalse();
        assertThat(service.getEventProfile("someone_else"))
                .isEqualTo(new DiscordMembersService.EventProfile(false, null));
        server.verify();
    }

    @Test void profileSharesMembershipCacheAndRefreshesServerNicknameAfterTenMinutes() {
        server.expect(requestTo(FIRST_PAGE)).andRespond(withSuccess(
                "[{\"user\":{\"id\":\"123456789012345679\",\"username\":\"operator\",\"global_name\":\"Global\"},\"nick\":\"server nickname\"}]",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(FIRST_PAGE)).andRespond(withSuccess(
                "[{\"user\":{\"id\":\"123456789012345679\",\"username\":\"operator\",\"global_name\":\"Global\"},\"nick\":null}]",
                MediaType.APPLICATION_JSON));
        assertThat(service.usernameExists("operator")).isTrue();
        assertThat(service.getEventProfile("operator"))
                .isEqualTo(new DiscordMembersService.EventProfile(true, "server nickname"));
        now.set(Duration.ofMinutes(10).toNanos());
        assertThat(service.getEventProfile("operator"))
                .isEqualTo(new DiscordMembersService.EventProfile(true, "Global"));
        assertThat(service.usernameExists("operator")).isTrue();
        server.verify();
    }

    @Test void loadsEveryPageBeforePublishingCache() {
        String first = IntStream.range(0, 1000)
                .mapToObj(i -> member(123456789012345679L + i, "user" + i)).collect(Collectors.joining(",", "[", "]"));
        server.expect(requestTo(FIRST_PAGE)).andRespond(withSuccess(first, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://discord.com/api/v10/guilds/123456789012345678/members?limit=1000&after=123456789012346678"))
                .andRespond(withSuccess("[" + member(123456789012346679L, "last_user") + "]", MediaType.APPLICATION_JSON));
        assertThat(service.usernameExists("last_user")).isTrue();
        assertThat(service.usernameExists("user0")).isTrue();
        server.verify();
    }

    @Test void failedRefreshDoesNotBecomeFalseOrAnEmptyCache() {
        server.expect(requestTo(FIRST_PAGE)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(FIRST_PAGE)).andRespond(withSuccess("[" + member(123456789012345679L, "operator") + "]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.usernameExists("operator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThat(service.usernameExists("operator")).isTrue();
        server.verify();
    }

    @Test void concurrentRequestsShareOneRefresh() throws Exception {
        server.expect(requestTo(FIRST_PAGE)).andRespond(withSuccess("[" + member(123456789012345679L, "operator") + "]", MediaType.APPLICATION_JSON));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                start.await();
                return service.usernameExists("operator");
            })).toList();
            start.countDown();
            for (var task : tasks) assertThat(task.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        server.verify();
    }
}
