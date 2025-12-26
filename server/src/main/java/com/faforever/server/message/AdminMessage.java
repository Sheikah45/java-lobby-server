package com.faforever.server.message;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public sealed interface AdminMessage extends LobbyMessage.Client {

    enum Action {
        @JsonProperty("broadcast") BROADCAST, @JsonProperty("closeFA") CLOSE_GAME, @JsonProperty("closelobby") CLOSE_LOBBY
    }

    @JsonCreator
    static AdminMessage of(@JsonProperty("action") Action action, @JsonAnySetter Map<String, String> properties) {
        return switch (action) {
            case BROADCAST -> new BroadcastRequest(properties.get("message"));
            case CLOSE_GAME -> new ClosePlayerGameRequest(Integer.parseInt(properties.get("user_id")));
            case CLOSE_LOBBY -> new ClosePlayerLobbyRequest(Integer.parseInt(properties.get("user_id")));
        };
    }

    record BroadcastRequest(String message) implements AdminMessage {}

    record ClosePlayerGameRequest(
            int playerId
    ) implements AdminMessage {}

    record ClosePlayerLobbyRequest(
            int playerId
    ) implements AdminMessage {}

}
