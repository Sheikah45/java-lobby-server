package com.faforever.server.message;

import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.internal.MessageRequest;
import com.faforever.server.utils.NoThrowCloseable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.MDC;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@ApplicationScoped
class MessageBroker {

    private final Map<Long, SessionHandler> sessionControllerMap = new ConcurrentHashMap<>();
    private final Map<Integer, Set<SessionHandler>> playerControllerMap = new ConcurrentHashMap<>();

    @RunOnVirtualThread
    @Incoming(value = "client-outbound")
    public void processMessageRequest(MessageRequest messageRequest) {
        try (NoThrowCloseable _ = populateMDC(messageRequest)) {
            switch (messageRequest) {
                case MessageRequest.ForSession(long sessionId, LobbyMessage.Server message) -> sendMessageForSession(sessionId, message);
                case MessageRequest.ForPlayer(int playerId, LobbyMessage.Server message) -> sendMessageForPlayer(playerId, message);
                case MessageRequest.KickPlayer(int playerId) -> kickPlayer(playerId);
            }
        }
    }

    private void kickPlayer(int playerId) {
        Set<SessionHandler> playerSessionHandlers = playerControllerMap.getOrDefault(playerId,
                Set.of());
        if (playerSessionHandlers.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessionHandlers.forEach(sessionController -> {
            sessionController.sendMessage(new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK));
            sessionController.close();
        });
    }

    private void sendMessageForPlayer(int playerId, LobbyMessage.Server message) {
        Set<SessionHandler> playerSessionHandlers = playerControllerMap.getOrDefault(playerId,
                Set.of());
        if (playerSessionHandlers.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessionHandlers.forEach(sessionController -> sessionController.sendMessage(message));
    }

    private void sendMessageForSession(long sessionId, LobbyMessage.Server message) {
        SessionHandler sessionHandler = sessionControllerMap.get(sessionId);
        if (sessionHandler == null) {
            LOG.warn("No active session for session id");
            return;
        }

        sessionHandler.sendMessage(message);
    }

    void registerSession(SessionHandler sessionHandler) {
        long sessionId = sessionHandler.sessionId();
        SessionHandler mappedSessionHandler = sessionControllerMap.putIfAbsent(sessionId,
                sessionHandler);
        if (mappedSessionHandler != sessionHandler) {
            throw new IllegalStateException("Existing session for id %d".formatted(sessionId));
        }

        int playerId = sessionHandler.playerId();
        playerControllerMap.computeIfAbsent(playerId,  _ -> ConcurrentHashMap.newKeySet()).add(sessionHandler);
    }

    public void unregisterSession(SessionHandler sessionHandler) {
        if (!sessionControllerMap.remove(sessionHandler.sessionId(), sessionHandler)) {
            LOG.warn("Session controller not associated with session id");
        }

        Set<SessionHandler> sessionHandlers = playerControllerMap.getOrDefault(sessionHandler.playerId(), Set.of());
        if (!sessionHandlers.remove(sessionHandler)) {
            LOG.warn("Session controller not associated with player");
        }

        if (sessionHandlers.isEmpty()) {
            playerControllerMap.remove(sessionHandler.playerId(), sessionHandlers);
        }
    }

    private NoThrowCloseable populateMDC(MessageRequest messageRequest) {
        switch (messageRequest) {
            case MessageRequest.KickPlayer(int playerId) -> MDC.put("playerId", playerId);
            case MessageRequest.ForPlayer(int playerId, _) -> MDC.put("playerId", playerId);
            case MessageRequest.ForSession(long sessionId, _) -> MDC.put("sessionId", sessionId);
        }

        return () -> {
            MDC.remove("sessionId");
            MDC.remove("playerId");
        };
    }
}
