package com.faforever.server.connection;


import com.faforever.server.message.external.LobbyMessage;

public interface LobbyConnection {

    void sendAndAwait(LobbyMessage.Server message);

    void close();

}
