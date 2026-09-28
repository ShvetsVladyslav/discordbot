package org.baddie.discordbot.command;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "service.commands.valentine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ValentineCommandComponent implements SlashCommandComponent {

    @Override
    public String name() {
        return "valentine";
    }

    @Override
    public CommandData commandData() {
        return Commands.slash("valentine", "Отправить анонимную валентинку в ЛС")
                .addOption(OptionType.USER, "recipient", "Получатель с сервера", true)
                .addOption(OptionType.STRING, "message", "Текст валентинки", true)
                .addOption(OptionType.ATTACHMENT, "media", "Медиафайл (опционально)", false);
    }

    @Override
    public void handle(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("Эту команду можно использовать только на сервере.").setEphemeral(true).queue();
            return;
        }

        User recipient = event.getOption("recipient") != null
                ? event.getOption("recipient").getAsUser()
                : null;
        String messageText = event.getOption("message") != null
                ? event.getOption("message").getAsString().trim()
                : "";
        Message.Attachment mediaAttachment = event.getOption("media") != null
                ? event.getOption("media").getAsAttachment()
                : null;

        if (recipient == null) {
            event.reply("Укажи получателя.").setEphemeral(true).queue();
            return;
        }
        if (messageText.isBlank()) {
            event.reply("Текст валентинки не может быть пустым.").setEphemeral(true).queue();
            return;
        }
        if (recipient.isBot()) {
            event.reply("Нельзя отправить валентинку боту.").setEphemeral(true).queue();
            return;
        }
        if (recipient.getId().equals(event.getUser().getId())) {
            event.reply("Нельзя отправить анонимную валентинку самому себе.").setEphemeral(true).queue();
            return;
        }

        String sparkle = emojiMention(event.getGuild(), "aestheticsparkles");
        String loveNote = emojiMention(event.getGuild(), "62363dlovenote");
        String aesthetic2 = emojiMention(event.getGuild(), "zaesthetic2");

        String dmText = sparkle + " " + sparkle + " " + sparkle + "  " + loveNote + "  ВНИМАНИЕ! " + loveNote + "  "
                + sparkle + " " + sparkle + " " + sparkle + "\n\n"
                + aesthetic2 + " Вам пришла анонимная валентинка!\n"
                + "Кто-то тайно вздыхает при виде вашего ника…\n"
                + "Возможно это любовь… возможно иди нахуй...\n\n"
                + sparkle + " " + sparkle + " " + sparkle + " Ваше тайное послание: "
                + sparkle + " " + sparkle + " " + sparkle + "\n\n"
                + messageText;

        event.deferReply(true).queue(hook ->
                recipient.openPrivateChannel().queue(
                        channel -> {
                            if (mediaAttachment == null) {
                                channel.sendMessage(dmText).queue(
                                        success -> hook.sendMessage("Валентинка отправлена анонимно.").queue(),
                                        error -> hook.sendMessage("Не удалось отправить ЛС. Возможно, у получателя закрыты личные сообщения.").queue()
                                );
                                return;
                            }

                            try (InputStream in = new URL(mediaAttachment.getUrl()).openStream()) {
                                byte[] bytes = in.readAllBytes();
                                FileUpload upload = FileUpload.fromData(bytes, mediaAttachment.getFileName());
                                channel.sendMessage(dmText)
                                        .addFiles(upload)
                                        .queue(
                                                success -> hook.sendMessage("Валентинка с медиа отправлена анонимно.").queue(),
                                                error -> hook.sendMessage("Не удалось отправить ЛС. Возможно, у получателя закрыты личные сообщения.").queue()
                                        );
                            } catch (Exception e) {
                                hook.sendMessage("Не удалось прикрепить медиафайл. Попробуй другой файл.").queue();
                            }
                        },
                        error -> hook.sendMessage("Не удалось отправить ЛС. Возможно, у получателя закрыты личные сообщения.").queue()
                )
        );
        log.info("Message was successfully sent");
    }

    private String emojiMention(Guild guild, String emojiName) {
        if (guild == null) return ":" + emojiName + ":";

        List<RichCustomEmoji> matches = guild.getEmojisByName(emojiName, true);
        if (matches.isEmpty()) return ":" + emojiName + ":";

        return matches.get(0).getAsMention();
    }
}
