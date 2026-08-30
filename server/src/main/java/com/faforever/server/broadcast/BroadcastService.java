package com.faforever.server.broadcast;

import com.faforever.server.connection.SessionController;
import com.faforever.server.message.LobbyMessage;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@JBossLog
@ApplicationScoped
public class BroadcastService {

    private final ExecutorService executorService;
    private final Set<SessionController> sessionControllers = ConcurrentHashMap.newKeySet();

    public BroadcastService(@VirtualThreads ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void broadcast(LobbyMessage.Broadcast message) {
        sessionControllers.stream()
                          .filter(SessionController::isActive)
                          .filter(SessionController::isAuthenticated)
                          .forEach(sessionController -> executorService.execute(() -> sessionController.broadcast(message)));
    }

    public void registerSession(SessionController sessionController) {
        sessionControllers.add(sessionController);
    }

    public void unregisterSession(SessionController sessionController) {
        sessionControllers.remove(sessionController);
    }



}
