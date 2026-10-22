package org.baddie.discordbot.members;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@ConditionalOnExpression("'${APP_ENV:local}' == 'local'")
@RequestMapping("/api/discord/members")
public class DiscordMembersController {
    private final DiscordMembersService service;

    public DiscordMembersController(DiscordMembersService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get members with selected roles")
    public List<DiscordMembersService.MemberResponse> getMembers() {
        return service.getMembers();
    }
}
