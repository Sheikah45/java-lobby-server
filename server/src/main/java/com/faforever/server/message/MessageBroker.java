package com.faforever.server.message;

import com.faforever.server.game.GPGService;
import com.faforever.server.game.GameService;
import com.faforever.server.matchmaker.MatchmakerService;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.external.GPGMessage;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.external.MatchmakerMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import com.faforever.server.message.internal.OutboundTarget;
import com.faforever.server.player.AdminService;
import com.faforever.server.player.PlayerService;
import com.faforever.server.utils.NoThrowCloseable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.jboss.logging.MDC;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@ApplicationScoped
@RequiredArgsConstructor
public class MessageBroker {

    private final AdminService adminService;
    private final MatchmakerService matchmakerService;
    private final GameService gameService;
    private final PlayerService playerService;
    private final GPGService gpgService;

    private final Map<Long, SessionHandler> sessionHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Long>> playerSessionMap = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public void handleInboundMessage(InboundLobbyMessage<?> inboundLobbyMessage) {
        switch (inboundLobbyMessage.message()) {
            case SocialMessage.Client _ ->
                    playerService.handleRequest((InboundLobbyMessage<SocialMessage.Client>) inboundLobbyMessage);
            case AdminMessage.Client _ ->
                    adminService.handleRequest((InboundLobbyMessage<AdminMessage.Client>) inboundLobbyMessage);
            case MatchmakerMessage.Client _ -> matchmakerService.handleRequest(
                    (InboundLobbyMessage<MatchmakerMessage.Client>) inboundLobbyMessage);
            case GameMessage.Client _ ->
                    gameService.handleRequest((InboundLobbyMessage<GameMessage.Client>) inboundLobbyMessage);
            case GPGMessage.Client _ ->
                    gpgService.handleRequest((InboundLobbyMessage<GPGMessage.Client>) inboundLobbyMessage);
        }
    }

    public void handleOutboundMessage(OutboundLobbyMessage<?> outboundLobbyMessage) {
        try (NoThrowCloseable _ = populateMDC(outboundLobbyMessage)) {
            LobbyMessage.Server message = outboundLobbyMessage.message();
            switch (outboundLobbyMessage.target()) {
                case OutboundTarget.Session(long sessionId) -> sendMessageToSession(sessionId, message);
                case OutboundTarget.Player(int playerId) -> sendMessageToPlayer(playerId, message);
                case OutboundTarget.All() -> sendMessageToAll(message);
            }
        }
    }

    private void sendMessageToAll(LobbyMessage.Server message) {
        sessionHandlerMap.keySet().forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToSession(long sessionId, LobbyMessage.Server message) {
        SessionHandler sessionHandler = sessionHandlerMap.get(sessionId);
        if (sessionHandler == null) {
            LOG.warn("No active session for session id");
            return;
        }

        sessionHandler.sendMessage(message);
        if (message instanceof AdminMessage.NoticeInfo(
                _, AdminMessage.Style style
        ) && style == AdminMessage.Style.KICK) {
            sessionHandler.close();
        }
    }

    private void sendMessageToPlayer(int playerId, LobbyMessage.Server message) {
        Set<Long> playerSessions = playerSessionMap.getOrDefault(playerId, Set.of());
        if (playerSessions.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessions.forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    void registerSession(SessionHandler sessionHandler) {
        long sessionId = sessionHandler.sessionId();
        SessionHandler mappedSessionHandler = sessionHandlerMap.putIfAbsent(sessionId, sessionHandler);
        if (mappedSessionHandler != null && mappedSessionHandler != sessionHandler) {
            throw new IllegalStateException("Existing session for id %d".formatted(sessionId));
        }

        int playerId = sessionHandler.playerId();
        playerSessionMap.computeIfAbsent(playerId, _ -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    void unregisterSession(SessionHandler sessionHandler) {
        if (!sessionHandlerMap.remove(sessionHandler.sessionId(), sessionHandler)) {
            LOG.warn("Session controller not associated with session id");
        }

        Set<Long> sessions = playerSessionMap.getOrDefault(sessionHandler.playerId(), Set.of());
        if (!sessions.remove(sessionHandler.sessionId())) {
            LOG.warn("Session controller not associated with player");
        }

        if (sessions.isEmpty()) {
            playerSessionMap.remove(sessionHandler.playerId(), sessions);
        }
    }

    private NoThrowCloseable populateMDC(OutboundLobbyMessage<?> outboundLobbyMessage) {
        switch (outboundLobbyMessage.target()) {
            case OutboundTarget.Player(int playerId) -> MDC.put("playerId", playerId);
            case OutboundTarget.Session(long sessionId) -> MDC.put("sessionId", sessionId);
            case OutboundTarget.All() -> {}
        }

        return () -> {
            MDC.remove("sessionId");
            MDC.remove("playerId");
        };
    }
}
