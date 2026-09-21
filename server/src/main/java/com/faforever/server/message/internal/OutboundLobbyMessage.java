package com.faforever.server.message.internal;

import com.faforever.server.message.external.LobbyMessage;

public record OutboundLobbyMessage<T extends LobbyMessage.Server>(
        OutboundTarget target,
        T message
) {

    public OutboundLobbyMessage {
        if (target instanceof OutboundTarget.All && !(message instanceof LobbyMessage.Broadcast)) {
            throw new IllegalArgumentException("Only broadcast messages can be sent to all players");
        }
    }

    public static <T extends LobbyMessage.Server> OutboundLobbyMessage<T> forPlayer(int playerId, T message) {
        return new OutboundLobbyMessage<>(new OutboundTarget.Player(playerId), message);
    }

    public static <T extends LobbyMessage.Server> OutboundLobbyMessage<T> forSession(long sessionId, T message) {
        return new OutboundLobbyMessage<>(new OutboundTarget.Session(sessionId), message);
    }

    public static <T extends LobbyMessage.Broadcast> OutboundLobbyMessage<T> forAll(T message) {
        return new OutboundLobbyMessage<>(new OutboundTarget.All(), message);
    }
}
