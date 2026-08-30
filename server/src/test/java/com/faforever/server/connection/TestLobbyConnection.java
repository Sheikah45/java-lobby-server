package com.faforever.server.connection;

import com.faforever.server.message.LobbyMessage;

import java.util.ArrayList;
import java.util.List;

public class TestLobbyConnection implements LobbyConnection {

    private final List<LobbyMessage.Server> sentMessages = new ArrayList<>();

    private boolean closed = false;

    public List<LobbyMessage.Server> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    @Override
    public void sendAndAwait(LobbyMessage.Server message) {
        if (closed) {
            throw new IllegalStateException("Connection closed");
        }
        sentMessages.add(message);
    }

    @Override
    public void close() {
        closed = true;
    }
}
