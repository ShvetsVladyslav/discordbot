package org.baddie.discordbot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

public interface SlashCommandComponent {

    String name();

    CommandData commandData();

    void handle(@NotNull SlashCommandInteractionEvent event);
}
