package com.faforever.server.endpoint.connection;


import com.faforever.server.message.external.LobbyMessage;

public interface LobbyConnection {

    void sendAndAwait(LobbyMessage.Server message);

    void close();

}
