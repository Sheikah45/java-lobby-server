package com.faforever.server.connection;


import com.faforever.server.message.LobbyMessage;
import io.smallrye.mutiny.Uni;

public class NoConnection implements LobbyConnection {

    private static final NoConnection INSTANCE = new NoConnection();

    public static NoConnection getInstance() {
        return INSTANCE;
    }

    private NoConnection() {}

    @Override
    public Uni<Void> send(LobbyMessage.Server message) {
        throw new IllegalStateException("No connection exists");
    }
}
