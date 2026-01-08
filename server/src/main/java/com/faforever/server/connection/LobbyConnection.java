package com.faforever.server.connection;


import com.faforever.server.message.LobbyMessage;
import io.smallrye.mutiny.Uni;

public interface LobbyConnection {

    void sendAndAwait(LobbyMessage.Server message);

    void close();

}
