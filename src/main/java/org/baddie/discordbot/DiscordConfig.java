package org.baddie.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.baddie.discordbot.command.SlashCommandComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class DiscordConfig {

    @Bean
    public JDA jda(
            @Value("${service.discord.token}") String token,
            @Value("${service.discord.guildId:}") String guildId,
            List<ListenerAdapter> listeners,
            List<SlashCommandComponent> slashCommands
    ) throws InterruptedException {

        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(listeners.toArray())
                .build();

        jda.awaitReady();

        var commandData = slashCommands.stream()
                .map(SlashCommandComponent::commandData)
                .collect(Collectors.toList());

        // Clear global commands to avoid stale definitions shadowing guild-scoped commands.
        jda.updateCommands().complete();

        if (guildId == null || guildId.isBlank()) {
            return jda;
        }

        for (Guild guild : jda.getGuilds()) {
            if (guild.getId().equals(guildId)) {
                guild.updateCommands()
                        .addCommands(commandData)
                        .complete();
            } else {
                // Keep other servers clean so users don't see outdated or test commands.
                guild.updateCommands().complete();
            }
        }

        return jda;
    }
}
