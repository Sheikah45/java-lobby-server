package com.faforever.server.message;

import com.faforever.server.config.FAFProperties;
import com.faforever.server.endpoint.connection.LobbyConnection;
import com.faforever.server.endpoint.connection.NoConnection;
import com.faforever.server.endpoint.connection.UserAgent;
import com.faforever.server.exception.ClientException;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.player.PlayerService;
import com.faforever.server.policy.PolicyClient;
import com.faforever.server.policy.PolicyContents;
import com.faforever.server.utils.NoThrowCloseable;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.MDC;
import org.jspecify.annotations.Nullable;

import java.util.SplittableRandom;

@JBossLog
@Dependent
public class SessionHandler {

    private final MessageBroker messageBroker;
    private final PlayerService playerService;

    private final FAFProperties fafProperties;

    private final PolicyClient policyClient;
    private final JWTParser jwtParser;

    private LobbyConnection connection = NoConnection.getInstance();
    private @Nullable UserAgent userAgent;
    private @Nullable Integer playerId;

    private boolean authenticated;

    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    public SessionHandler(MessageBroker messageBroker, PlayerService playerService, FAFProperties fafProperties,
                          @RestClient PolicyClient policyClient, JWTParser jwtParser) {
        this.messageBroker = messageBroker;
        this.playerService = playerService;
        this.fafProperties = fafProperties;
        this.policyClient = policyClient;
        this.jwtParser = jwtParser;
    }

    @PreDestroy
    void teardown() {
        try (NoThrowCloseable _ = populateMDC()) {
            messageBroker.unregisterSession(this);
        }
    }

    public void setConnection(LobbyConnection connection) {
        if (!(this.connection instanceof NoConnection)) {
            throw new IllegalStateException("Connection already set for session");
        }
        if (connection instanceof NoConnection) {
            throw new IllegalArgumentException("Connection being set is not active");
        }
        this.connection = connection;
    }

    public void clearConnection() {
        playerService.unregisterSession(sessionId);
        connection.close();
        connection = NoConnection.getInstance();
    }

    private void setUserAgent(UserAgent userAgent) {
        if (this.userAgent != null) {
            LOG.warn("User agent already set for session");
            return;
        }
        LOG.debugf("Detected user agent %s", userAgent);
        this.userAgent = userAgent;
    }

    @Nullable UserAgent userAgent() {
        return userAgent;
    }

    int playerId() {
        if (playerId == null) {
            throw new IllegalStateException("Not authenticated");
        }
        return playerId;
    }

    long sessionId() {
        return sessionId;
    }

    void close() {
        connection.close();
    }

    void sendMessage(LobbyMessage.Server message) {
        connection.sendAndAwait(message);
    }

    public void handleMessage(LobbyMessage.Client message) {
        try (NoThrowCloseable _ = populateMDC()) {
            switch (message) {
                case ConnectionMessage.Client connectionMessage -> handleConnectionMessage(connectionMessage);
                case LobbyMessage.Authenticated authenticatedMessage ->
                        messageBroker.handleInboundMessage(wrapMessage(authenticatedMessage));
            }
        }
    }

    private void handleConnectionMessage(ConnectionMessage.Client message) {
        switch (message) {
            case ConnectionMessage.Ping() -> sendMessage(new ConnectionMessage.Pong());
            case ConnectionMessage.Pong() -> {}
            case ConnectionMessage.SessionRequest(String agent, String version) -> {
                setUserAgent(new UserAgent(agent, version));
                sendMessage(new ConnectionMessage.SessionResponse(sessionId));
            }
            case ConnectionMessage.AuthenticateRequest(String token, String uniqueId) -> {
                if (this.playerId != null) {
                    throw new IllegalStateException("Player already set for the session");
                }

                JsonWebToken jwt;
                try {
                    jwt = jwtParser.parse(token);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                playerId = Integer.parseInt(jwt.getSubject());
                if (fafProperties.usePolicyServer()) {
                    policyClient.checkPolicy(new PolicyContents(sessionId, playerId, uniqueId));
                }
                messageBroker.registerSession(this);
                playerService.registerSessionForPlayer(sessionId, playerId);
                authenticated = true;
            }
        }
    }

    private <T extends LobbyMessage.Authenticated> InboundLobbyMessage<T> wrapMessage(T message) {
        if (!authenticated) {
            throw new ClientException("Not authenticated");
        }
        if (playerId == null) {
            throw new IllegalStateException("Not associated with a player");
        }
        return new InboundLobbyMessage<>(sessionId, playerId, message);
    }

    private NoThrowCloseable populateMDC() {
        MDC.put("sessionId", sessionId);
        if (playerId != null) {
            MDC.put("playerId", playerId);
        }

        return () -> {
            MDC.remove("sessionId");
            MDC.remove("playerId");
        };
    }

}
