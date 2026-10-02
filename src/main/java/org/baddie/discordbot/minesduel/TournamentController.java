package org.baddie.discordbot.minesduel;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.baddie.discordbot.minesduel.dto.TournamentDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final JDA jda;

    @Value("${service.tournament.api-key}")
    private String apiKey;

    @PostMapping("/collect")
    public CompletableFuture<TournamentDtos.CollectedPlayers> collect(
            @RequestHeader("X-Tournament-Key") String key,
            @RequestBody TournamentDtos.CollectRequest request
    ) {
        authorize(key);

        if (request == null
                || !validId(request.guildId())
                || !validId(request.channelId())
                || !validId(request.messageId())
                || request.emoji() == null
                || request.emoji().isBlank()) {
            throw badRequest(
                    "Передай guildId, channelId, messageId и emoji."
            );
        }

        var guild = jda.getGuildById(request.guildId());

        if (guild == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Сервер не найден среди серверов бота."
            );
        }

        var rawChannel = guild.getGuildChannelById(request.channelId());

        if (!(rawChannel instanceof GuildMessageChannel channel)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Канал сообщений не найден на этом сервере."
            );
        }

        Emoji emoji;
        try {
            emoji = Emoji.fromFormatted(request.emoji());
        } catch (IllegalArgumentException error) {
            throw badRequest("Некорректная эмоция.");
        }

        Map<String, User> users = new ConcurrentHashMap<>();

        return channel.retrieveMessageById(request.messageId())
                .submit()
                .thenCompose(message ->
                        message.retrieveReactionUsers(emoji)
                                .limit(100)
                                .cache(false)
                                .forEachAsync(user -> {
                                    if (!user.isBot()) {
                                        users.put(user.getId(), user);
                                    }
                                    return true;
                                })
                )
                .thenCompose(ignored -> {
                    List<User> orderedUsers = users.values().stream()
                            .sorted(Comparator.comparing(User::getId))
                            .toList();

                    CompletableFuture<List<TournamentDtos.Player>> result =
                            CompletableFuture.completedFuture(
                                    new ArrayList<>()
                            );

                    // Получаем серверные ники последовательно.
                    // Не отправляем сотни запросов одновременно.
                    for (User user : orderedUsers) {
                        result = result.thenCompose(players ->
                                guild.retrieveMemberById(user.getId())
                                        .submit()
                                        .handle((member, error) -> {
                                            if (error != null) {
                                                Throwable cause = unwrap(error);

                                                // Пользователь уже ушёл с сервера.
                                                if (cause instanceof ErrorResponseException e
                                                        && e.getErrorResponse()
                                                        == ErrorResponse.UNKNOWN_MEMBER) {
                                                    return players;
                                                }

                                                throw new CompletionException(cause);
                                            }

                                            players.add(new TournamentDtos.Player(
                                                    member.getId(),
                                                    member.getEffectiveName()
                                            ));

                                            return players;
                                        })
                        );
                    }

                    return result;
                })
                .thenApply(players -> new TournamentDtos.CollectedPlayers(
                        request.guildId(),
                        request.channelId(),
                        request.messageId(),
                        request.emoji(),
                        Instant.now(),
                        List.copyOf(players)
                ))
                .exceptionally(error -> {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Не удалось собрать участников из Discord. "
                                    + "Проверь сообщение, эмоцию и доступ бота к каналу.",
                            unwrap(error)
                    );
                });
    }

    @PostMapping("/bracket")
    public TournamentDtos.Bracket bracket(
            @RequestHeader("X-Tournament-Key") String key,
            @RequestBody TournamentDtos.BracketRequest request
    ) {
        authorize(key);

        if (request == null
                || request.players() == null
                || request.players().size() < 2
                || request.players().size() > 1024) {
            throw badRequest("Нужно от 2 до 1024 участников.");
        }

        Set<String> ids = new HashSet<>();

        for (TournamentDtos.Player player : request.players()) {
            if (player == null
                    || !validId(player.discordId())
                    || player.displayName() == null
                    || player.displayName().isBlank()) {
                throw badRequest("У каждого игрока нужны discordId и displayName.");
            }

            if (!ids.add(player.discordId())) {
                throw badRequest("Один Discord ID указан несколько раз.");
            }
        }

        List<TournamentDtos.Player> players = new ArrayList<>(request.players());

        // Сортировка делает результат воспроизводимым
        // даже при другом порядке входного списка.
        players.sort(Comparator.comparing(TournamentDtos.Player::discordId));

        long seed = request.seed() != null
                ? request.seed()
                : ThreadLocalRandom.current().nextLong();

        Collections.shuffle(players, new Random(seed));

        int bracketSize = 2;
        while (bracketSize < players.size()) {
            bracketSize *= 2;
        }

        // Располагаем участников так, чтобы проходы без матча
        // распределялись по сетке, а пустых пар не было.
        List<Integer> positions = new ArrayList<>(List.of(1, 2));

        for (int width = 4; width <= bracketSize; width *= 2) {
            List<Integer> next = new ArrayList<>();

            for (int position : positions) {
                next.add(position);
                next.add(width + 1 - position);
            }

            positions = next;
        }

        List<TournamentDtos.Match> firstRound = new ArrayList<>();

        for (int i = 0; i < bracketSize; i += 2) {
            TournamentDtos.Player a = playerAt(players, positions.get(i));
            TournamentDtos.Player b = playerAt(players, positions.get(i + 1));

            boolean bye = a == null || b == null;

            firstRound.add(new TournamentDtos.Match(
                    "R1-M" + (i / 2 + 1),
                    a,
                    b,
                    null,
                    null,
                    bye ? (a != null ? a : b) : null,
                    bye ? "BYE" : "READY"
            ));
        }

        List<TournamentDtos.Round> rounds = new ArrayList<>();
        rounds.add(new TournamentDtos.Round(1, List.copyOf(firstRound)));

        List<TournamentDtos.Match> previous = firstRound;
        int roundNumber = 2;

        while (previous.size() > 1) {
            List<TournamentDtos.Match> current = new ArrayList<>();

            for (int i = 0; i < previous.size(); i += 2) {
                TournamentDtos.Match sourceA = previous.get(i);
                TournamentDtos.Match sourceB = previous.get(i + 1);

                TournamentDtos.Player a = sourceA.winner();
                TournamentDtos.Player b = sourceB.winner();

                current.add(new TournamentDtos.Match(
                        "R" + roundNumber + "-M" + (i / 2 + 1),
                        a,
                        b,
                        sourceA.id(),
                        sourceB.id(),
                        null,
                        a != null && b != null ? "READY" : "WAITING"
                ));
            }

            rounds.add(new TournamentDtos.Round(roundNumber, List.copyOf(current)));
            previous = current;
            roundNumber++;
        }

        return new TournamentDtos.Bracket(
                "SINGLE_ELIMINATION",
                seed,
                players.size(),
                bracketSize,
                List.copyOf(rounds)
        );
    }

    private static TournamentDtos.Player playerAt(List<TournamentDtos.Player> players, int position) {
        return position <= players.size()
                ? players.get(position - 1)
                : null;
    }

    private void authorize(String suppliedKey) {
        if (apiKey == null
                || apiKey.isBlank()
                || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private static boolean validId(String value) {
        return value != null && value.matches("[0-9]{17,20}");
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}