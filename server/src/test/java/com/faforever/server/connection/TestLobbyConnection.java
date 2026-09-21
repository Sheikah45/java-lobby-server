package com.faforever.server.connection;

import com.faforever.server.endpoint.connection.LobbyConnection;
import com.faforever.server.message.external.LobbyMessage;

import java.util.ArrayList;
import java.util.List;

public class TestLobbyConnection implements LobbyConnection {

    private final List<LobbyMessage.Server> sentMessages = new ArrayList<>();

    private boolean closed = false;

    public List<LobbyMessage.Server> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    public void reset() {
        sentMessages.clear();
        closed = false;
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void sendAndAwait(LobbyMessage.Server message) {
        if (closed) {
            throw new IllegalStateException("Test connection closed");
        }
        sentMessages.add(message);
    }

    @Override
    public void close() {
        closed = true;
    }
}
