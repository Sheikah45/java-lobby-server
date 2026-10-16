package com.faforever.server.message.client;

public sealed interface LobbyMessage permits LobbyMessage.Client, LobbyMessage.Server {

    sealed interface Server extends LobbyMessage permits MatchmakerMessage.Server {}

    sealed interface Client extends LobbyMessage permits MatchmakerMessage.Client{}

    sealed interface Broadcast extends Server permits MatchmakerMessage.MatchmakerInfo {}

}
