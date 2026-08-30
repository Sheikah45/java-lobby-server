package com.faforever.server.endpoint.websocket;

import com.faforever.server.connection.LobbyConnection;
import com.faforever.server.message.LobbyMessage;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LobbyJsonWebsocketConnection implements LobbyConnection {

    private final WebSocketConnection delegate;

    @Override
    public void sendAndAwait(LobbyMessage.Server message) {
        delegate.sendTextAndAwait(message);
    }

    @Override
    public void close() {
        delegate.closeAndAwait();
    }
}
