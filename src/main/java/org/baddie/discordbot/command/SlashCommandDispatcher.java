package org.baddie.discordbot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SlashCommandDispatcher extends ListenerAdapter {

    private final Map<String, SlashCommandComponent> commands;

    public SlashCommandDispatcher(List<SlashCommandComponent> commands) {
        this.commands = commands.stream()
                .collect(Collectors.toMap(SlashCommandComponent::name, Function.identity()));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        SlashCommandComponent command = commands.get(event.getName());
        if (command == null) return;
        command.handle(event);
    }
}
