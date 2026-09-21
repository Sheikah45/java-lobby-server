package com.faforever.server.player;

import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import com.faforever.server.message.external.AdminMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class AdminService {

    private final UserGroupAssignmentRepository userGroupAssignmentRepository;

    private final MessageBroker messageBroker;

    public void handleRequest(InboundLobbyMessage<AdminMessage.Client> request) {
        int playerId = request.playerId();
        switch (request.message()) {
            case AdminMessage.BroadcastRequest broadcastRequest -> broadcast(playerId, broadcastRequest);
            case AdminMessage.KickPlayerRequest kickPlayerRequest -> kickPlayer(playerId, kickPlayerRequest);
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest -> closePlayerGame(playerId, closePlayerGameRequest);
        }
    }

    private void broadcast(int requestorPlayerId, AdminMessage.BroadcastRequest broadcastRequest) {
        String message = broadcastRequest.message();
        if (message.isBlank()) {
            return;
        }

         if (userGroupAssignmentRepository.playerLacksPermission(requestorPlayerId, "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warnf("Unauthorized broadcast request: %s", broadcastRequest.message());
            return;
        }

        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO)));
    }

    private void kickPlayer(int requestorPlayerId, AdminMessage.KickPlayerRequest kickPlayerRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(requestorPlayerId, "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %d", kickPlayerRequest.playerId());
            return;
        }

        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forPlayer(kickPlayerRequest.playerId(), new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)));
    }

    private void closePlayerGame(int requestorPlayerId, AdminMessage.ClosePlayerGameRequest closePlayerGameRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(requestorPlayerId, "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %d", closePlayerGameRequest.playerId());
            return;
        }

        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forPlayer(closePlayerGameRequest.playerId(), new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)));
    }


}
