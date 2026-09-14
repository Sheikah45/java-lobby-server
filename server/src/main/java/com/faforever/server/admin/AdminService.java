package com.faforever.server.admin;

import com.faforever.server.game.GameService;
import com.faforever.server.message.MessageEmitter;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.internal.MessageRequest;
import com.faforever.server.player.PlayerService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class AdminService {

    private final PlayerService playerService;
    private final GameService gameService;

    private final MessageEmitter messageEmitter;

    public void handleRequest(AdminRequest request) {
        switch (request) {
            case AdminRequest.Broadcast broadcastRequest -> broadcast(broadcastRequest);
            case AdminRequest.KickPlayer kickPlayerRequest -> kickPlayer(kickPlayerRequest);
            case AdminRequest.ClosePlayerGame closePlayerGameRequest -> closePlayerGame(closePlayerGameRequest);
        }
    }

    private void broadcast(AdminRequest.Broadcast broadcastRequest) {
        String message = broadcastRequest.message();
        if (message.isBlank()) {
            return;
        }

;        if (playerService.sessionLacksPermission(broadcastRequest.requestorSessionId(), "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warnf("Unauthorized broadcast request: %s", broadcastRequest.message());
            return;
        }

        playerService.broadcast(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO));
    }

    private void kickPlayer(AdminRequest.KickPlayer kickPlayerRequest) {
        if (playerService.sessionLacksPermission(kickPlayerRequest.requestorSessionId(), "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %s", kickPlayerRequest.playerId());
            return;
        }

        messageEmitter.send(new MessageRequest.KickPlayer(kickPlayerRequest.playerId()));
    }

    private void closePlayerGame(AdminRequest.ClosePlayerGame closePlayerGameRequest) {
        if (playerService.sessionLacksPermission(closePlayerGameRequest.requestorSessionId(), "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %s", closePlayerGameRequest.playerId());
            return;
        }

        gameService.closePlayerGame(closePlayerGameRequest.playerId());
    }


}
