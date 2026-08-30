package com.faforever.server.message;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public sealed interface AdminMessage {

    sealed interface Server extends AdminMessage, LobbyMessage.Server {}

    sealed interface Client extends AdminMessage, LobbyMessage.Client {}

    enum Action {
        @JsonProperty("broadcast") BROADCAST, @JsonProperty("closeFA") CLOSE_GAME, @JsonProperty("closelobby") CLOSE_LOBBY
    }

    @JsonCreator
    static AdminMessage.Client of(@JsonProperty("action") Action action, @JsonAnySetter Map<String, String> properties) {
        return switch (action) {
            case BROADCAST -> new BroadcastRequest(properties.get("message"));
            case CLOSE_GAME -> new ClosePlayerGameRequest(Integer.parseInt(properties.get("user_id")));
            case CLOSE_LOBBY -> new KickPlayerRequest(Integer.parseInt(properties.get("user_id")));
        };
    }

    record BroadcastRequest(String message) implements Client {}

    record ClosePlayerGameRequest(
            int playerId
    ) implements Client {}

    record KickPlayerRequest(
            int playerId
    ) implements Client {}

    record NoticeInfo(
            @Nullable String message,
            Style style
    ) implements Server, LobbyMessage.Broadcast {}

    enum Style {
        @JsonProperty("info") INFO,
        @JsonProperty("error") ERROR,
        @JsonProperty("kick") KICK,
        @JsonProperty("kill") KILL
    }

}
