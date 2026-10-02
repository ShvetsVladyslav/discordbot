package org.baddie.discordbot.minesduel.dto;

import java.time.Instant;
import java.util.List;

public class TournamentDtos {

    private TournamentDtos() {
    }

    public record CollectRequest(
            String guildId,
            String channelId,
            String messageId,
            String emoji
    ) {
    }

    public record Player(
            String discordId,
            String displayName
    ) {
    }

    public record CollectedPlayers(
            String guildId,
            String channelId,
            String messageId,
            String emoji,
            Instant collectedAt,
            List<Player> players
    ) {
    }

    public record BracketRequest(
            List<Player> players,
            Long seed
    ) {
    }

    public record Bracket(
            String format,
            long seed,
            int playerCount,
            int bracketSize,
            List<Round> rounds
    ) {
    }

    public record Round(
            int number,
            List<Match> matches
    ) {
    }

    public record Match(
            String id,
            Player playerA,
            Player playerB,
            String sourceMatchA,
            String sourceMatchB,
            Player winner,
            String status
    ) {
    }
}
