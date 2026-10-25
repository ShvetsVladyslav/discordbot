package org.baddie.discordbot.members;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventMembershipControllerTests {
    private final DiscordMembersService service = mock(DiscordMembersService.class);
    private final EventMembershipController controller = new EventMembershipController(service);

    @Test void delegatesExactUsernameAndReturnsBoolean() {
        when(service.usernameExists("operator")).thenReturn(true);
        assertThat(controller.exists("operator")).isTrue();
        assertThat(controller.exists("unknown")).isFalse();
        verify(service).usernameExists("operator");
        verify(service).usernameExists("unknown");
    }

    @Test void rejectsBlankOrOversizedUsername() {
        for (String username : new String[]{"", "   ", "x".repeat(33)}) {
            assertThatThrownBy(() -> controller.exists(username))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> controller.profile(username))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(service);
    }

    @Test void profileDelegatesExactUsername() {
        var profile = new DiscordMembersService.EventProfile(true, "server nickname");
        when(service.getEventProfile("operator")).thenReturn(profile);
        assertThat(controller.profile("operator")).isEqualTo(profile);
        verify(service).getEventProfile("operator");
    }

    @Test void profileJsonIncludesAvatarUrlAlongsideExistingFields() throws Exception {
        var avatar = "https://cdn.discordapp.com/avatars/123456789012345679/" + "a".repeat(32) + ".png?size=512";
        when(service.getEventProfile("luckerfourleaf")).thenReturn(new DiscordMembersService.EventProfile(
                true, "это sunny", java.util.List.of("998350106023698432"), "2020-06-01T00:00:00Z", avatar));
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/event/members/profile")
                        .param("username", "luckerfourleaf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.member").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.displayName").value("это sunny"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.roles[0]").value("998350106023698432"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.joined_at").value("2020-06-01T00:00:00Z"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.avatarUrl").value(avatar));
    }
}
