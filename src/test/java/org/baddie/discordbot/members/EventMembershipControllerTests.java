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
        }
        verifyNoInteractions(service);
    }
}
