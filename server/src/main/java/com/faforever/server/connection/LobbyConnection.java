package com.faforever.server.connection;


import com.faforever.server.message.LobbyMessage;
import io.smallrye.mutiny.Uni;

public interface LobbyConnection {

    default void sendAndAwait(LobbyMessage.Server message) {
        send(message).await().indefinitely();
    }

    Uni<Void> send(LobbyMessage.Server message);

    void close();


}
