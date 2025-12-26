package com.faforever.server.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public sealed interface LobbyMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = ConnectionMessage.SessionResponse.class, name = "session"),
                    @JsonSubTypes.Type(value = ConnectionMessage.LoginSuccessResponse.class, name = "welcome"),
                    @JsonSubTypes.Type(value = SocialMessage.PlayerInfoList.class, name = "player_info"),
                    @JsonSubTypes.Type(value = SocialMessage.AvatarInfoList.class, name = "avatar"),
                    @JsonSubTypes.Type(value = SocialMessage.SocialInfo.class, name = "social"),
            }
    )
    sealed interface Server extends LobbyMessage permits ConnectionMessage.Server, Broadcast, SocialMessage.Server {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = ConnectionMessage.SessionRequest.class, name = "ask_session"),
                    @JsonSubTypes.Type(value = ConnectionMessage.AuthenticateRequest.class, name = "auth"),
                    @JsonSubTypes.Type(value = SocialMessage.SocialAddRequest.class, name = "social_add"),
                    @JsonSubTypes.Type(value = SocialMessage.SocialRemoveRequest.class, name = "social_remove"),
                    @JsonSubTypes.Type(value = SocialMessage.AvatarRequest.class, name = "avatar"),
                    @JsonSubTypes.Type(value = AdminMessage.class, name = "admin"),

            }
    )
    sealed interface Client extends LobbyMessage permits AdminMessage, ConnectionMessage.Client, MatchmakerMessage.Client, SocialMessage.Client {}

    sealed interface Broadcast extends Server permits ConnectionMessage.Ping, SocialMessage.PlayerInfoList {}

}
