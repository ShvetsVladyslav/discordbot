package org.baddie.discordbot.members;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.stream.Collectors;

@RestController
@ConditionalOnExpression("'${APP_ENV:local}' == 'local'")
@RequestMapping("/api/discord/members")
public class DiscordMembersController {
    private final DiscordMembersService service;

    public DiscordMembersController(DiscordMembersService service) {
        this.service = service;
    }

    @GetMapping(produces = "text/plain;charset=UTF-8")
    @Operation(summary = "Get members as tab-separated text for Google Sheets")
    public String getMembers() {
        return "ник\tроль\n" + service.getMembers().stream()
                .map(member -> cell(member.nick()) + "\t" + cell(String.join(", ", member.roles())))
                .collect(Collectors.joining("\n"));
    }

    private static String cell(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
