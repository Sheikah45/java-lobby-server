package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.GameAccess;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.LobbyMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface GameMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = GameMessage.HostGameRequest.class, name = "game_host"),
                    @JsonSubTypes.Type(value = GameMessage.JoinGameRequest.class, name = "game_join"),
                    @JsonSubTypes.Type(value = GameMessage.RestoreGameSessionRequest.class, name = "restore_game_session"),
            }
    )
    sealed interface Client extends GameMessage, LobbyMessage.Client {}

    sealed interface Server extends GameMessage, LobbyMessage.Server {}

    record GameInfoList(
            List<GameInfo> games
    ) implements Server, LobbyMessage.Broadcast {}

    record GameJoinFailed(
            String reason,
            @JsonProperty("uid")
            int gameId
    ) implements Server {}

    record GameLaunchResponse(
            @JsonProperty("uid") int gameId,
            String name,
            @JsonProperty("mod") String featuredMod,
            @JsonProperty("init_mode") LobbyMode lobbyMode,
            GameType gameType,
            @JsonProperty("rating_type") String leaderboard,
            @JsonProperty("mapname") String mapName,
            @Nullable Integer mapPosition,
            @Nullable Integer expectedPlayers,
            Map<String, String> gameOptions,
            @Nullable Integer team,
            @Nullable Faction faction
    ) implements Server {

        @JsonProperty("args")
        public List<String> args() {
            return List.of();
        }

    }

    record HostGameRequest(
            @JsonProperty("mapname") @Nullable String mapName,
            String title,
            @JsonProperty("mod") @Nullable String featuredMod,
            @Deprecated boolean[] options,
            @Deprecated Integer version,
            GameAccess access,
            @Nullable String password,
            GameVisibility visibility,
            @Nullable Integer ratingMin,
            @Nullable Integer ratingMax,
            Boolean enforceRatingRange
    ) implements Client {}

    record JoinGameRequest(
            @JsonProperty("uid") int gameId,
            @Nullable String password
    ) implements Client {}

    record RestoreGameSessionRequest(
            int gameId
    ) implements Client {}
}
