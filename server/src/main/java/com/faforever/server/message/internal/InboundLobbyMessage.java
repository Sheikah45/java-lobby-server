package com.faforever.server.message.internal;

import com.faforever.server.message.external.LobbyMessage;

public record InboundLobbyMessage<T extends LobbyMessage.Authenticated>(
        long sessionId,
        int playerId,
        T message
) {}
