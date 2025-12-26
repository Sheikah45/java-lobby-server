package com.faforever.server.broadcast;

import com.faforever.server.message.LobbyMessage;
import com.faforever.server.session.SessionController;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class BroadcastService {

    private final Set<SessionController> sessionControllers = ConcurrentHashMap.newKeySet();

    public void broadcast(LobbyMessage.Broadcast message) {
        for (SessionController sessionController : sessionControllers) {
            if (!sessionController.isActive()) {
                continue;
            }
            sessionController.broadcast(message);
        }
    }

    public void registerSession(SessionController sessionController) {
        sessionControllers.add(sessionController);
    }

    public void unregisterSession(SessionController sessionController) {
        sessionControllers.remove(sessionController);
    }



}
