package com.faforever.server.message.client.dto;

import com.faforever.server.game.Game;
import com.faforever.server.game.RatingRange;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

public record GameInfo(
        int uid,
        String title,
        String host,
        GameType gameType,
        int maxPlayers,
        int numberOfPlayers,
        GameVisibility visibility,
        boolean passwordProtected,
        GameStatus state,
        String featuredMod,
        @JsonProperty("rating_type") String leaderboard,
        Map<String, String> simMods,
        String mapName,
        String mapFilePath,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime hostedAt,
        @Nullable Long launchedAt,
        @Nullable Integer ratingMin,
        @Nullable Integer ratingMax,
        boolean enforceRatingRange
) {

    public static GameInfo from(Game game) {
        Long launchedAt = game.getLaunchedAt() == null ? null : game.getLaunchedAt().toEpochSecond();
        RatingRange ratingRange = game.getRatingRange();
        return new GameInfo(game.getId(), game.getTitle(), game.getHost().getUsername(), game.getGameType(), 0, 0,
                game.getVisibility(), game.getPassword().isPresent(), null, game.getFeaturedMod(),
                game.getLeaderboard().technicalName(), Map.of(), game.getMapName(), game.getMapName(),
                game.getHostedAt(), launchedAt, ratingRange.min(), ratingRange.max(),
                game.isEnforceRatingRange());
    }

}
