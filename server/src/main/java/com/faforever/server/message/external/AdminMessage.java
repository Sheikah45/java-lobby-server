package com.faforever.server.message.external;

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
            case BROADCAST -> {
                String message = properties.get("message");
                if (message == null) {
                    throw new IllegalArgumentException("Missing required property `message`");
                }
                yield new BroadcastRequest(message);
            }
            case CLOSE_GAME -> {
                String userId = properties.get("user_id");
                if (userId == null) {
                    throw new IllegalArgumentException("Missing required property `user_id`");
                }
                yield new ClosePlayerGameRequest(Integer.parseInt(userId));
            }
            case CLOSE_LOBBY -> {
                String userId = properties.get("user_id");
                if (userId == null) {
                    throw new IllegalArgumentException("Missing required property `user_id`");
                }
                yield new KickPlayerRequest(Integer.parseInt(userId));
            }
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
