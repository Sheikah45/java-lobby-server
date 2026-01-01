package com.faforever.server.message;

import com.faforever.server.admin.AdminService;
import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.ConnectionService;
import com.faforever.server.game.GameService;
import com.faforever.server.matchmaker.MatchmakerService;
import com.faforever.server.social.SocialService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class MessageBroker {

    private final ConnectionService connectionService;
    private final SocialService socialService;
    private final AdminService adminService;
    private final MatchmakerService matchmakerService;
    private final GameService gameService;
    private final BroadcastService broadcastService;

    public void handleMessage(LobbyMessage.Client message) {
        switch (message) {
            case ConnectionMessage.Ping() -> connectionService.pong();
            case ConnectionMessage.Pong() -> {}
            case ConnectionMessage.SessionRequest sessionRequest -> connectionService.updateSession(sessionRequest);
            case ConnectionMessage.AuthenticateRequest authenticateRequest ->
                    connectionService.authenticate(authenticateRequest);
            case SocialMessage.SocialAddRequest addRequest -> socialService.addSocialRelationship(addRequest);
            case SocialMessage.SocialRemoveRequest removeRequest ->
                    socialService.removeSocialRelationship(removeRequest);
            case SocialMessage.ListAvatarsRequest _ -> socialService.sendAvatarList();
            case SocialMessage.SelectAvatarRequest selectRequest -> socialService.selectAvatar(selectRequest);
            case AdminMessage.BroadcastRequest broadcastRequest -> adminService.broadcast(broadcastRequest);
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest -> {}
            case AdminMessage.ClosePlayerLobbyRequest closePlayerLobbyRequest -> adminService.kickPlayer(closePlayerLobbyRequest);
            case MatchmakerMessage.GameMatchmakingRequest gameMatchmakingRequest -> {}
            case MatchmakerMessage.InviteToPartyRequest inviteToPartyRequest -> {}
            case MatchmakerMessage.AcceptInviteToPartyRequest acceptInviteToPartyRequest -> {}
            case MatchmakerMessage.IsReadyResponse isReadyResponse -> {}
            case MatchmakerMessage.KickPlayerFromPartyRequest kickPlayerFromPartyRequest -> {}
            case MatchmakerMessage.LeavePartyRequest _ -> {}
            case MatchmakerMessage.MatchmakerInfoRequest _ -> {}
            case MatchmakerMessage.SelectPartyFactionsRequest selectPartyFactionsRequest -> {}
            case MatchmakerMessage.SetPlayerVetoesRequest setPlayerVetoesRequest -> {}
            case MatchmakerMessage.UnreadyPartyRequest _ -> {}
            case GameMessage.HostGameRequest hostGameRequest -> {}
            case GameMessage.JoinGameRequest joinGameRequest -> {}
            case GameMessage.RestoreGameSessionRequest restoreGameSessionRequest -> {}
        }
    }

}
