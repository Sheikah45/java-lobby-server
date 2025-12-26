package com.faforever.server.message;

import com.faforever.server.admin.AdminService;
import com.faforever.server.connection.ConnectionService;
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

    public void handleMessage(LobbyMessage.Client message) {
        switch (message) {
            case ConnectionMessage.Client connectionMessage -> connectionService.handleMessage(connectionMessage);
            case SocialMessage.Client socialMessage -> socialService.handleMessage(socialMessage);
            case AdminMessage adminMessage -> adminService.handleMessage(adminMessage);
        }
    }

}
