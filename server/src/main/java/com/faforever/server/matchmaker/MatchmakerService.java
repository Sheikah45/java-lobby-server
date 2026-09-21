package com.faforever.server.matchmaker;

import com.faforever.server.message.external.MatchmakerMessage;
import com.faforever.server.message.internal.InboundLobbyMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class MatchmakerService {

    public void handleRequest(InboundLobbyMessage<MatchmakerMessage.Client> request) {
        switch (request.message()) {
            case MatchmakerMessage.GameMatchmakingRequest _ -> {}
            case MatchmakerMessage.InviteToPartyRequest _ -> {}
            case MatchmakerMessage.AcceptInviteToPartyRequest _ -> {}
            case MatchmakerMessage.IsReadyResponse _ -> {}
            case MatchmakerMessage.KickPlayerFromPartyRequest _ -> {}
            case MatchmakerMessage.LeavePartyRequest _ -> {}
            case MatchmakerMessage.MatchmakerInfoRequest _ -> {}
            case MatchmakerMessage.SelectPartyFactionsRequest _ -> {}
            case MatchmakerMessage.SetPlayerVetoesRequest _ -> {}
            case MatchmakerMessage.UnreadyPartyRequest _ -> {}
        }
    }

}
