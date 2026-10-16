package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.PlayerInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;
import org.mariadb.jdbc.message.ServerMessage;

import java.time.OffsetDateTime;

public sealed interface ConnectionMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = ConnectionMessage.LoginSuccessResponse.class, name = "welcome"),
                   @JsonSubTypes.Type(value = ConnectionMessage.LoginFailureResponse.class, name = "authentication_failure"),})
    sealed interface Server extends ConnectionMessage, LobbyMessage.Server {}

    record LoginSuccessResponse(
            PlayerInfo me,
            OffsetDateTime currentTime
    ) implements Server {}

    record LoginFailureResponse(
            @Nullable String text
    ) implements Server {}

}
