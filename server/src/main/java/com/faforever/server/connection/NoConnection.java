package com.faforever.server.connection;


import com.faforever.server.message.LobbyMessage;

public class NoConnection implements LobbyConnection {

    private static final NoConnection INSTANCE = new NoConnection();

    public static NoConnection getInstance() {
        return INSTANCE;
    }

    private NoConnection() {}

    @Override
    public void sendAndAwait(LobbyMessage.Server message) {
        throw new IllegalStateException("No connection exists");
    }

    @Override
    public void close() {}
}
