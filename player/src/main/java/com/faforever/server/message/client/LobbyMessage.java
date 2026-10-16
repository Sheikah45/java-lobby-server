package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public sealed interface LobbyMessage permits LobbyMessage.Client, LobbyMessage.Server {

    sealed interface Server extends LobbyMessage permits AdminMessage.Server, ConnectionMessage.Server, Broadcast, SocialMessage.Server {}

    sealed interface Client extends LobbyMessage permits AdminMessage.Client, SocialMessage.Client {}

    sealed interface Broadcast extends Server permits AdminMessage.NoticeInfo, SocialMessage.PlayerInfoList {}

}
