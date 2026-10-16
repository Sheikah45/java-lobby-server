package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public sealed interface LobbyMessage permits GPGMessage, LobbyMessage.Client, LobbyMessage.Server {

    sealed interface Server extends LobbyMessage permits GPGMessage.Server, GameMessage.Server, Broadcast {}

    sealed interface Client extends LobbyMessage permits GPGMessage.Client, GameMessage.Client{}

    sealed interface Broadcast extends Server permits GameMessage.GameInfoList {}

}
