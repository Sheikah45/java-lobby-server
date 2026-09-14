package com.faforever.server.message.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

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
    String hostedAt,
    double launchedAt,
    @Nullable Integer ratingMin,
    @Nullable Integer ratingMax,
    boolean enforceRatingRange
) {}
