package com.faforever.server.admin;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.SessionController;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerRepository;
import com.faforever.server.player.PlayerService;
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
        Player player = sessionController.getPlayer();
        if (message.isBlank() || playerRepository.playerHasPermission(player, "ADMIN_BROADCAST_MESSAGE")) {
            return;
        }

        broadcastService.broadcast(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO));
    }

    @Transactional
    public void kickPlayer(AdminMessage.ClosePlayerLobbyRequest closePlayerLobbyRequest) {
        Player player = sessionController.getPlayer();
        if (playerRepository.playerHasPermission(player, "ADMIN_KICK_SERVER")) {
            return;
        }

        playerService.kickPlayer(closePlayerLobbyRequest.playerId());
    }


}
