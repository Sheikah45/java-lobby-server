package com.faforever.server.admin;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.SessionController;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.social.PlayerRepository;
import com.faforever.server.social.PlayerService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class AdminService {

    private final PlayerService playerService;
    private final BroadcastService broadcastService;

    private final SessionController sessionController;

    private final PlayerRepository playerRepository;

    @Transactional
    public void broadcast(AdminMessage.BroadcastRequest broadcastRequest) {
        String message = broadcastRequest.message();
        int playerId = sessionController.getPlayerId();
        if (message.isBlank() || playerRepository.playerHasPermission(playerId, "ADMIN_BROADCAST_MESSAGE")) {
            return;
        }

        broadcastService.broadcast(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO));
    }

    @Transactional
    public void kickPlayer(AdminMessage.ClosePlayerLobbyRequest closePlayerLobbyRequest) {
        int playerId = sessionController.getPlayerId();
        if (playerRepository.playerHasPermission(playerId, "ADMIN_KICK_SERVER")) {
            return;
        }

        playerService.kickPlayer(closePlayerLobbyRequest.playerId());
    }


}
