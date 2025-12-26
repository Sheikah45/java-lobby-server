package com.faforever.server.websocket;

import com.faforever.server.connection.LobbyConnection;
import com.faforever.server.message.LobbyMessage;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LobbyJsonWebsocketConnection implements LobbyConnection {

    private final WebSocketConnection delegate;

    @Override
    public Uni<Void> send(LobbyMessage.Server message) {
        return delegate.sendText(message);
    }
}
