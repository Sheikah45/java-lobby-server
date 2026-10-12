package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.PlayerInfo;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

public sealed interface ConnectionMessage {

    sealed interface Server extends ConnectionMessage, LobbyMessage.Server {}

    record LoginSuccessResponse(
            PlayerInfo me,
            OffsetDateTime currentTime
    ) implements Server {}

    record LoginFailureResponse(
            @Nullable String text
    ) implements Server {}

}
