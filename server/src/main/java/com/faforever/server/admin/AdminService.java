package com.faforever.server.admin;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.player.PlayerRepository;
import com.faforever.server.player.PlayerService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class AdminService {

    private final PlayerService playerService;
    private final BroadcastService broadcastService;

    private final PlayerRepository playerRepository;

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

        if (!playerRepository.playerHasPermission(broadcastRequest.requestorId(), "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warnf("Unauthorized broadcast request: %s", broadcastRequest.message());
            return;
        }

        broadcastService.broadcast(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO));
    }

    private void kickPlayer(AdminRequest.KickPlayer kickPlayerRequest) {
        if (!playerRepository.playerHasPermission(kickPlayerRequest.requestorId(), "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %s", kickPlayerRequest.playerId());
            return;
        }

        playerService.kickPlayer(kickPlayerRequest.playerId());
    }

    private void closePlayerGame(AdminRequest.ClosePlayerGame closePlayerGameRequest) {
        if (!playerRepository.playerHasPermission(closePlayerGameRequest.requestorId(), "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %s", closePlayerGameRequest.playerId());
            return;
        }

        playerService.kickPlayer(closePlayerGameRequest.playerId());
    }


}
