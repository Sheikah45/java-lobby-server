package com.faforever.server.message;

import com.faforever.server.admin.AdminRequest;
import com.faforever.server.admin.AdminService;
import com.faforever.server.connection.LobbyConnection;
import com.faforever.server.connection.NoConnection;
import com.faforever.server.connection.UserAgent;
import com.faforever.server.exception.ClientException;
import com.faforever.server.game.GPGService;
import com.faforever.server.game.Game;
import com.faforever.server.game.GameService;
import com.faforever.server.matchmaker.MatchmakerService;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.GPGMessage;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.external.MatchmakerMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.external.dto.DtoMapper;
import com.faforever.server.player.PlayerService;
import com.faforever.server.player.SocialRequest;
import com.faforever.server.utils.NoThrowCloseable;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.MDC;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.SplittableRandom;

@JBossLog
@RequiredArgsConstructor
@Dependent
public class SessionHandler {

    private final MessageBroker messageBroker;
    private final AdminService adminService;
    private final MatchmakerService matchmakerService;
    private final GameService gameService;
    private final PlayerService playerService;
    private final GPGService gpgService;

    private final JWTParser jwtParser;

    private final DtoMapper dtoMapper;

    private LobbyConnection connection = NoConnection.getInstance();
    private @Nullable UserAgent userAgent;
    private @Nullable Integer playerId;
    private @Nullable Game game;

    private boolean authenticated;

    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    @PreDestroy
    private void teardown() {
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

    private void launchGame(Game game) {
        if (this.game != null && !Objects.equals(this.game, game)) {
            throw new IllegalStateException("Game already set for the session");
        }

        if (Objects.equals(this.game, game)) {
            LOG.warn("Game already launched");
            return;
        }

        LOG.debug("Launching game");
        this.game = game;
        connection.sendAndAwait(dtoMapper.mapToGameLaunch(game));
    }

    void close() {
        connection.close();
    }

    public void sendMessage(LobbyMessage.Server message) {
        connection.sendAndAwait(message);
    }

    public void handleMessage(LobbyMessage.Client message) {
        try (NoThrowCloseable _ = populateMDC()) {
            switch (message) {
                case ConnectionMessage.Client connectionMessage -> handleConnectionMessage(connectionMessage);
                case SocialMessage.Client socialMessage -> handleSocialMessage(socialMessage);
                case AdminMessage.Client adminMessage -> handleAdminMessage(adminMessage);
                case MatchmakerMessage.Client matchmakerMessage -> handleMatchmakerMessage(matchmakerMessage);
                case GameMessage.Client gameMessage -> handleGameMessage(gameMessage);
                case GPGMessage.Client gpgMessage -> handleGpgMessage(gpgMessage);
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
                messageBroker.registerSession(this);
                playerService.registerSessionForPlayer(sessionId, playerId);
                authenticated = true;
            }
        }
    }

    private void handleSocialMessage(SocialMessage.Client message) {
        checkAuthenticated();
        switch (message) {
            case SocialMessage.SocialAddRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Add(sessionId, friendId,
                            SocialRequest.FriendOrFoe.Status.FRIEND));
                }
                if (foeId != null) {
                    playerService.changeSocialRelationship(
                            new SocialRequest.FriendOrFoe.Add(sessionId, foeId, SocialRequest.FriendOrFoe.Status.FOE));
                }
            }
            case SocialMessage.SocialRemoveRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    playerService.changeSocialRelationship(
                            new SocialRequest.FriendOrFoe.Remove(sessionId, friendId));
                }
                if (foeId != null) {
                    playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(sessionId, foeId));
                }
            }
            case SocialMessage.SelectAvatarRequest(String avatarUrl) ->
                    playerService.selectAvatar(new SocialRequest.SelectAvatar(sessionId, avatarUrl));
            case SocialMessage.RemoveAvatarRequest _ ->
                    playerService.removeAvatar(new SocialRequest.RemoveAvatar(sessionId));
            case SocialMessage.ListAvatarsRequest _ -> playerService.sendAvatars(new SocialRequest.Avatars(sessionId));
        }
    }

    private void handleAdminMessage(AdminMessage.Client message) {
        checkAuthenticated();
        AdminRequest adminRequest = switch (message) {
            case AdminMessage.BroadcastRequest(String broadcastMessage) ->
                    new AdminRequest.Broadcast(sessionId, broadcastMessage);
            case AdminMessage.KickPlayerRequest(int kickPlayerId) ->
                    new AdminRequest.KickPlayer(sessionId, kickPlayerId);
            case AdminMessage.ClosePlayerGameRequest(int closePlayerId) ->
                    new AdminRequest.ClosePlayerGame(sessionId, closePlayerId);
        };
        adminService.handleRequest(adminRequest);
    }

    private void handleMatchmakerMessage(MatchmakerMessage.Client message) {
        checkAuthenticated();
        switch (message) {
            case MatchmakerMessage.GameMatchmakingRequest _ -> {}
            case MatchmakerMessage.InviteToPartyRequest _ -> {}
            case MatchmakerMessage.AcceptInviteToPartyRequest _ -> {}
            case MatchmakerMessage.IsReadyResponse _ -> {}
            case MatchmakerMessage.KickPlayerFromPartyRequest _ -> {}
            case MatchmakerMessage.LeavePartyRequest _ -> {}
            case MatchmakerMessage.MatchmakerInfoRequest _ -> {}
            case MatchmakerMessage.SelectPartyFactionsRequest _ -> {}
            case MatchmakerMessage.SetPlayerVetoesRequest _ -> {}
            case MatchmakerMessage.UnreadyPartyRequest _ -> {}
        }
    }

    private void handleGameMessage(GameMessage.Client message) {
        checkAuthenticated();
        switch (message) {
            case GameMessage.HostGameRequest hostGameRequest -> {
                Game game = gameService.hostGame(sessionId, hostGameRequest);
                launchGame(game);
            }
            case GameMessage.JoinGameRequest _ -> {}
            case GameMessage.RestoreGameSessionRequest _ -> {}
        }
    }

    private void handleGpgMessage(GPGMessage.Client message) {
        checkAuthenticated();
        gpgService.handleClientMessage(sessionId, message);
    }

    private void checkAuthenticated() {
        if (!authenticated) {
            throw new ClientException("Not authenticated");
        }
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
