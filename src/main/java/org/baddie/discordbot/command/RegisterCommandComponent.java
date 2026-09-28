package org.baddie.discordbot.command;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.baddie.discordbot.SheetsWriter;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "service.commands.register", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RegisterCommandComponent extends ListenerAdapter implements SlashCommandComponent {

    private static final String BTN_OPEN_MODAL = "reg:open_modal";
    private static final String MODAL_ID = "reg:modal";

    private final SheetsWriter sheetsWriter;

    public RegisterCommandComponent(SheetsWriter sheetsWriter) {
        this.sheetsWriter = sheetsWriter;
    }

    @Override
    public String name() {
        return "register";
    }

    @Override
    public CommandData commandData() {
        return Commands.slash("register", "Tournament registration");
    }

    @Override
    public void handle(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("This command must be used on a server.").setEphemeral(true).queue();
            return;
        }

        event.reply("Click the button to open registration form:")
                .addActionRow(Button.primary(BTN_OPEN_MODAL, "Open form"))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.getComponentId().equals(BTN_OPEN_MODAL)) return;

        TextInput roles = TextInput.create("roles", "Roles (main/off)", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("for example: mid/jng")
                .setMinLength(3).setMaxLength(20)
                .build();

        TextInput peakElo = TextInput.create("peak_elo", "Peak rank from last season", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("for example: emerald 2 / d4 / master")
                .setMinLength(2).setMaxLength(32)
                .build();

        TextInput accLink = TextInput.create("lol_acc", "Link to League of Graphs/OP.GG", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("https://www.leagueofgraphs.com/summoner/...")
                .build();

        TextInput mainRoleChar = TextInput.create("main_role_char", "Champions for main role", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("for example: Jinx, KaiSa, Jhin")
                .build();

        TextInput offRoleChar = TextInput.create("off_role_char", "Champions for off role", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("for example: Ekko, KhaZix")
                .build();

        Modal modal = Modal.create(MODAL_ID, "Tournament registration")
                .addActionRow(roles)
                .addActionRow(peakElo)
                .addActionRow(accLink)
                .addActionRow(mainRoleChar)
                .addActionRow(offRoleChar)
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().equals(MODAL_ID)) return;

        String rolesRaw = event.getValue("roles").getAsString().trim();
        String peakEloRaw = event.getValue("peak_elo").getAsString().trim();
        String accLinkRaw = event.getValue("lol_acc").getAsString().trim();
        String mainCharactersRaw = event.getValue("main_role_char").getAsString().trim();
        String offCharactersRaw = event.getValue("off_role_char").getAsString().trim();

        ParsedRoles roles;
        try {
            roles = parseRoles(rolesRaw);
        } catch (IllegalArgumentException ex) {
            event.reply("Roles are invalid. Format: mid/jng. Allowed: top, jng, mid, adc, sup")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String discordId = event.getUser().getId();
        String discordName = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();

        try {
            sheetsWriter.appendRow(List.of(
                    OffsetDateTime.now().toString(),
                    discordId,
                    discordName,
                    roles.mainRole,
                    roles.offRole,
                    peakEloRaw,
                    accLinkRaw,
                    mainCharactersRaw,
                    offCharactersRaw
            ));

            event.reply("Registration submitted successfully.").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("Could not save your registration to the table.").setEphemeral(true).queue();
        }
    }

    private ParsedRoles parseRoles(String raw) {
        if (raw == null) throw new IllegalArgumentException("roles empty");

        String v = raw.trim().toLowerCase().replaceAll("\\s+", "");
        String[] parts = v.split("/");

        if (parts.length != 2) {
            throw new IllegalArgumentException("format must be main/off, e.g. mid/jng");
        }

        String main = normalizeRole(parts[0]);
        String off = normalizeRole(parts[1]);

        if (main.equals(off)) {
            throw new IllegalArgumentException("main and off roles must differ");
        }

        return new ParsedRoles(main, off);
    }

    private String normalizeRole(String s) {
        return switch (s) {
            case "top" -> "top";
            case "jg", "jng", "jungle" -> "jng";
            case "mid" -> "mid";
            case "adc", "bot" -> "adc";
            case "supp", "sup", "support" -> "sup";
            default -> throw new IllegalArgumentException("unknown role: " + s);
        };
    }

    private record ParsedRoles(String mainRole, String offRole) {
    }
}
