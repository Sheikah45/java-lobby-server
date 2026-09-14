package com.faforever.server.message.internal;

import com.faforever.server.message.external.LobbyMessage;

public sealed interface MessageRequest {

    record ForSession(
            long sessionId,
            LobbyMessage.Server message
    ) implements MessageRequest {}

    record ForPlayer(
            int playerId,
            LobbyMessage.Server message
    ) implements MessageRequest {}

    record KickPlayer(
            int playerId
    ) implements MessageRequest {}
}
