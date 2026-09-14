package com.faforever.server.message.external;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public sealed interface LobbyMessage permits GPGMessage, LobbyMessage.Client, LobbyMessage.Server {

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
                    @JsonSubTypes.Type(value = MatchmakerMessage.IsReadyRequest.class, name = "is_ready"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.PartyKick.class, name = "kicked_from_party"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.PartyInvite.class, name = "party_invite"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.PartyInfo.class, name = "update_party"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.SearchInfo.class, name = "search_info"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.VetoesChangedInfo.class, name = "vetoes_changed"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.MatchmakerMatchFoundResponse.class, name = "match_found"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.MatchmakerMatchCancelledResponse.class, name = "match_cancelled"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.MatchmakerInfo.class, name = "matchmaker_info"),
                    @JsonSubTypes.Type(value = GameMessage.GameInfoList.class, name = "game_info"),
                    @JsonSubTypes.Type(value = GameMessage.GameLaunchResponse.class, name = "game_launch"),
                    @JsonSubTypes.Type(value = AdminMessage.NoticeInfo.class, name = "notice"),
                    @JsonSubTypes.Type(value = GPGMessage.HostGame.class, name = "HostGame")

            }
    )
    sealed interface Server extends LobbyMessage permits AdminMessage.Server, ConnectionMessage.Server, GPGMessage.Server, GameMessage.Server, Broadcast, MatchmakerMessage.Server, SocialMessage.Server {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = GPGMessage.Client.class, visible = true)
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = ConnectionMessage.SessionRequest.class, name = "ask_session"),
                    @JsonSubTypes.Type(value = ConnectionMessage.AuthenticateRequest.class, name = "auth"),
                    @JsonSubTypes.Type(value = SocialMessage.SocialAddRequest.class, name = "social_add"),
                    @JsonSubTypes.Type(value = SocialMessage.SocialRemoveRequest.class, name = "social_remove"),
                    @JsonSubTypes.Type(value = SocialMessage.AvatarRequest.class, name = "avatar"),
                    @JsonSubTypes.Type(value = AdminMessage.Client.class, name = "admin"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.InviteToPartyRequest.class, name = "invite_to_party"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.AcceptInviteToPartyRequest.class, name = "accept_party_invite"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.KickPlayerFromPartyRequest.class, name = "kick_player_from_party"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.SelectPartyFactionsRequest.class, name = "set_party_factions"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.UnreadyPartyRequest.class, name = "unready_party"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.LeavePartyRequest.class, name = "leave_party"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.GameMatchmakingRequest.class, name = "game_matchmaking"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.SetPlayerVetoesRequest.class, name = "set_player_vetoes"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.MatchmakerInfoRequest.class, name = "matchmaker_info"),
                    @JsonSubTypes.Type(value = MatchmakerMessage.IsReadyResponse.class, name = "is_ready_response"),
                    @JsonSubTypes.Type(value = GameMessage.HostGameRequest.class, name = "game_host"),
                    @JsonSubTypes.Type(value = GameMessage.JoinGameRequest.class, name = "game_join"),
                    @JsonSubTypes.Type(value = GameMessage.RestoreGameSessionRequest.class, name = "restore_game_session"),
            }
    )
    sealed interface Client extends LobbyMessage permits AdminMessage.Client, ConnectionMessage.Client, GPGMessage.Client, GameMessage.Client, MatchmakerMessage.Client, SocialMessage.Client {}

    sealed interface Broadcast extends Server permits AdminMessage.NoticeInfo, ConnectionMessage.Ping, GameMessage.GameInfoList, MatchmakerMessage.MatchmakerInfo, SocialMessage.PlayerInfoList {}

}
