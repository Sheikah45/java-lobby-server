package com.faforever.server.admin;

import com.faforever.server.message.AdminMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class AdminService {

    public void handleMessage(AdminMessage message) {
        switch (message) {
            case AdminMessage.BroadcastRequest(String broadcastMessage) -> {}
            case AdminMessage.ClosePlayerGameRequest(int playerId) -> {}
            case AdminMessage.ClosePlayerLobbyRequest(int playerId) -> {}
        }

    }

}
