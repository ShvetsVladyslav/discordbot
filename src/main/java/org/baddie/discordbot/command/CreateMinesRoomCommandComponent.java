package org.baddie.discordbot.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import org.baddie.discordbot.minesduel.MinesDuelApiService;
import org.baddie.discordbot.minesduel.dto.CreateRoomReqDto;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "service.commands.mines", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CreateMinesRoomCommandComponent extends ListenerAdapter implements SlashCommandComponent {

    private static final String BUTTON_PREFIX = "mines:open:";
    private static final String SELECT_PREFIX = "mines:opponent:";

    private final MinesDuelApiService minesDuelApiService;

    private static final String MINES_ROOM_CREATOR_INVITATION_LINK = "https://mine-duel-room.burni.chatgpt.site/?room={roomId}&player=creator";
    private static final String MINES_ROOM_OPPONENT_INVITATION_LINK = "https://mine-duel-room.burni.chatgpt.site/?room={roomId}&player=opponent";

    @Override
    public String name() {
        return "minesroom";
    }

    @Override
    public CommandData commandData() {
        return Commands.slash("minesroom", "Вызвать игрока на дуэль в сапер");
    }

    @Override
    public void handle(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("Используй команду в чате сервера.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.reply("Выбери соперника для дуэли в сапере.")
                .setEphemeral(true)
                .addActionRow(
                        Button.primary(
                                BUTTON_PREFIX + event.getUser().getId(),
                                "Вызвать на дуэль"
                        )
                )
                .queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (!componentId.startsWith(BUTTON_PREFIX)) {
            return;
        }

        String ownerId = componentId.substring(BUTTON_PREFIX.length());

        if (!ownerId.equals(event.getUser().getId())) {
            event.reply("Это кнопка другого пользователя.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!event.isFromGuild()) {
            event.reply("Выбор соперника доступен только на сервере.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        EntitySelectMenu menu = EntitySelectMenu
                .create(
                        SELECT_PREFIX + ownerId,
                        EntitySelectMenu.SelectTarget.USER
                )
                .setPlaceholder("Кого хотим вызвать на дуэль?")
                .setRequiredRange(1, 1)
                .build();

        // Редактируем исходное приватное сообщение.
        event.editMessage(
                        "Кого хотим вызвать на дуэль?\n"
                                + "Введи имя для поиска или выбери участника "
                                + "из списка. После выбора приглашения "
                                + "будут отправлены в ЛС."
                )
                .setComponents(ActionRow.of(menu))
                .queue();
    }

    @Override
    public void onEntitySelectInteraction(
            @NotNull EntitySelectInteractionEvent event
    ) {
        String componentId = event.getComponentId();

        if (!componentId.startsWith(SELECT_PREFIX)) {
            return;
        }

        String ownerId = componentId.substring(SELECT_PREFIX.length());

        if (!ownerId.equals(event.getUser().getId())) {
            event.reply("Это выбор другого пользователя.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        var guild = event.getGuild();
        List<User> selectedUsers = event.getMentions().getUsers();

        if (guild == null || selectedUsers.size() != 1) {
            event.reply("Выбери одного участника сервера.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        User opponentUser = selectedUsers.get(0);

        if (opponentUser.getIdLong() == event.getUser().getIdLong()) {
            event.reply("Нельзя вызвать на дуэль самого себя.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (opponentUser.isBot()) {
            event.reply("Выбери человека, а не бота.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Убираем меню и подтверждаем взаимодействие
        // до выполнения запросов к Discord и API сайта.
        event.editMessage("Создаю комнату и отправляю приглашения…")
                .setComponents(Collections.emptyList())
                .queue(hook -> {
                    guild.retrieveMemberById(event.getUser().getIdLong())
                            .submit()
                            .thenCombine(
                                    guild.retrieveMemberById(
                                            opponentUser.getIdLong()
                                    ).submit(),
                                    DuelMembers::new
                            )
                            .thenCompose(members -> createAndInvite(
                                    members.creator(),
                                    members.opponent()
                            ))
                            .whenComplete((message, error) -> {
                                if (error != null) {
                                    log.error(
                                            "Не удалось создать дуэль в сапере",
                                            error
                                    );

                                    hook.editOriginal(
                                            "Не удалось создать дуэль. "
                                                    + "Проверь, что соперник "
                                                    + "находится на сервере, "
                                                    + "и повтори /minesroom."
                                    ).queue();
                                    return;
                                }

                                hook.editOriginal(message).queue();
                            });
                });
    }

    private CompletableFuture<String> createAndInvite(
            Member creator,
            Member opponent
    ) {
        CreateRoomReqDto request = CreateRoomReqDto.builder()
                .creatorName(creator.getEffectiveName())
                .opponentName(opponent.getEffectiveName())
                .build();

        // RestTemplate выполняет синхронный запрос.
        // Выносим его из потока обработки событий JDA.
        return CompletableFuture.supplyAsync(
                () -> minesDuelApiService.createRoom(request)
        ).thenCompose(room -> {
            if (room == null
                    || room.getCode() == null
                    || !room.getCode().matches("[A-F0-9]{8}")) {
                throw new IllegalStateException(
                        "API вернул некорректный код комнаты"
                );
            }

            String creatorLink = MINES_ROOM_CREATOR_INVITATION_LINK
                    .replace("{roomId}", room.getCode());

            String opponentLink = MINES_ROOM_OPPONENT_INVITATION_LINK
                    .replace("{roomId}", room.getCode());

            CompletableFuture<Boolean> creatorDelivery =
                    sendInvitation(creator.getUser(), creatorLink);

            CompletableFuture<Boolean> opponentDelivery =
                    sendInvitation(opponent.getUser(), opponentLink);

            return creatorDelivery.thenCombine(
                    opponentDelivery,
                    (creatorSent, opponentSent) -> {
                        if (creatorSent && opponentSent) {
                            return "Приглашения отправлены вам обоим в ЛС.";
                        }

                        StringBuilder result = new StringBuilder();

                        if (creatorSent) {
                            result.append(
                                    "Твоё приглашение отправлено в ЛС.\n"
                            );
                        } else {
                            result.append(
                                            "Не удалось отправить тебе ЛС."
                                    )
                                    .append("\nТвоя ссылка: ")
                                    .append(creatorLink)
                                    .append("\n");
                        }

                        if (opponentSent) {
                            result.append(
                                    "Приглашение сопернику отправлено."
                            );
                        } else {
                            result.append(
                                    "Не удалось отправить ЛС сопернику. "
                                            + "Попроси его разрешить личные "
                                            + "сообщения от участников сервера "
                                            + "и повтори /minesroom."
                            );
                        }

                        return result.toString();
                    }
            );
        });
    }

    private CompletableFuture<Boolean> sendInvitation(
            User user,
            String link
    ) {
        return user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(
                        "Вас вызвали на дуэль в сапере\n" + link
                ))
                .submit()
                .handle((message, error) -> {
                    if (error != null) {
                        log.warn(
                                "Не удалось отправить приглашение пользователю {}",
                                user.getId(),
                                error
                        );
                        return false;
                    }

                    return true;
                });
    }

    private record DuelMembers(Member creator, Member opponent) {
    }
}
