package com.faforever.server.message;

import com.faforever.server.message.dto.GameAccess;
import com.faforever.server.message.dto.GameInfo;
import com.faforever.server.message.dto.GameType;
import com.faforever.server.message.dto.GameVisibility;
import com.faforever.server.message.dto.LobbyMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface GameMessage {

    sealed interface Client extends GameMessage, LobbyMessage.Client {}

    sealed interface Server extends GameMessage, LobbyMessage.Server {}

    record GameInfoList(
            List<GameInfo> games
    ) implements Server, LobbyMessage.Broadcast {}

    record GameLaunchResponse(
            @JsonProperty("uid") int gameId,
            String name,
            @JsonProperty("mod") String featuredMod,
            LobbyMode lobbyMode,
            GameType gameType,
            @JsonProperty("rating_type") String leaderboard,
            @JsonProperty("mapname") String mapName,
            @Nullable Integer mapPosition,
            @Nullable Integer expectedPlayers,
            Map<String, String> gameOptions,
            @Nullable Integer team,
            @Nullable Faction faction
    ) implements Server {}

    record HostGameRequest(
            @JsonProperty("mapname") String mapName,
            String title,
            @JsonProperty("mod") String featuredMod,
            GameAccess access,
            String password,
            GameVisibility visibility,
            @Nullable Integer ratingMin,
            @Nullable Integer ratingMax,
            boolean enforceRatingRange
    ) implements Client {}

    record JoinGameRequest(
            @JsonProperty("uid") int gameId,
            @Nullable String password
    ) implements Client {}

    record RestoreGameSessionRequest(
            int gameId
    ) implements Client {}
}
