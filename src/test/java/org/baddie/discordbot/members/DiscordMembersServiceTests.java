package org.baddie.discordbot.members;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordMembersServiceTests {
    @Test
    void keepsOnlySelectedRolesAndMapsTheirNames() {
        var member = new DiscordMembersService.DiscordMember("nickname",
                List.of("unknown", "1012647364256870411", "998350106023698432"), null);
        var result = DiscordMembersService.filter(member);
        assertEquals("nickname", result.nick());
        assertEquals(List.of("сладенькие", "mommy"), result.roles());
    }

    @Test
    void fallsBackToGlobalNameThenUsername() {
        var user = new DiscordMembersService.DiscordUser("123", "login", "display");
        assertEquals("display", DiscordMembersService.filter(
                new DiscordMembersService.DiscordMember(null, List.of(), user)).nick());
        var withoutGlobalName = new DiscordMembersService.DiscordUser("123", "login", null);
        assertEquals("login", DiscordMembersService.filter(
                new DiscordMembersService.DiscordMember(null, List.of(), withoutGlobalName)).nick());
    }

    @Test
    void unrelatedRolesDoNotQualify() {
        var result = DiscordMembersService.filter(new DiscordMembersService.DiscordMember(
                "nickname", List.of("unknown"), null));
        assertTrue(result.roles().isEmpty());
    }
}
