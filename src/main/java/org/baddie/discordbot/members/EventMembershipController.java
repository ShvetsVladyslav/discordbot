package org.baddie.discordbot.members;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/event/members")
public class EventMembershipController {
    private final DiscordMembersService service;

    public EventMembershipController(DiscordMembersService service) {
        this.service = service;
    }

    @GetMapping("/exists")
    @Operation(summary = "Check guild membership by Discord username (10-minute cache)")
    public boolean exists(@RequestParam String username) {
        validateUsername(username);
        return service.usernameExists(username);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get guild display name by Discord username (shared 10-minute cache)")
    public DiscordMembersService.EventProfile profile(@RequestParam String username) {
        validateUsername(username);
        return service.getEventProfile(username);
    }

    private static void validateUsername(String username) {
        if (username.isBlank() || username.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Discord username");
        }
    }
}
