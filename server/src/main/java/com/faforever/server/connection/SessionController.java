package com.faforever.server.connection;

import com.faforever.server.admin.AdminRequest;
import com.faforever.server.admin.AdminService;
import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.exception.ClientException;
import com.faforever.server.game.GPGService;
import com.faforever.server.game.Game;
import com.faforever.server.game.GameService;
import com.faforever.server.matchmaker.MatchmakerService;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.GPGMessage;
import com.faforever.server.message.GameMessage;
import com.faforever.server.message.LobbyMessage;
import com.faforever.server.message.MatchmakerMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.player.Avatar;
import com.faforever.server.player.PlayerService;
import com.faforever.server.player.SocialRequest;
import com.faforever.server.utils.NoThrowCloseable;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.Dependent;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.MDC;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;

@JBossLog
@RequiredArgsConstructor
@Dependent
public class SessionController {

    private final BroadcastService broadcastService;
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

    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    public boolean isAuthenticated() {
        return playerId != null;
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
        playerService.unregisterSession(this);
        broadcastService.unregisterSession(this);
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

    public Optional<UserAgent> userAgent() {
        return Optional.ofNullable(userAgent);
    }

    void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public Optional<Integer> playerId() {
        return Optional.ofNullable(playerId);
    }

    public Optional<Game> game() {
        return Optional.ofNullable(game);
    }

    public boolean isActive() {
        return !(connection instanceof NoConnection);
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
        game.addGameConnection(this);
        connection.sendAndAwait(dtoMapper.mapToGameLaunch(game));
    }

    public void clearGame() {
        this.game = null;
    }

    public void kick() {
        connection.sendAndAwait(new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK));
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


    private NoThrowCloseable populateMDC() {
        MDC.put("sessionId", sessionId);
        playerId().ifPresent(playerId -> MDC.put("playerId", playerId));
        game().map(Game::getDetails).map(Game.Details::id).ifPresent(gameId -> MDC.put("gameId", gameId));

        return () -> {
            MDC.remove("sessionId");
            MDC.remove("playerId");
            MDC.remove("gameId");
        };
    }

    private void handleConnectionMessage(ConnectionMessage.Client message) {
        switch (message) {
            case ConnectionMessage.Ping() -> connection.sendAndAwait(new ConnectionMessage.Pong());
            case ConnectionMessage.Pong() -> {}
            case ConnectionMessage.SessionRequest(String agent, String version) -> {
                setUserAgent(new UserAgent(agent, version));
                connection.sendAndAwait(new ConnectionMessage.SessionResponse(sessionId));
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
                setPlayerId(Integer.parseInt(jwt.getSubject()));
                playerService.registerSession(this);
                broadcastService.registerSession(this);
            }
        }
    }

    private void handleSocialMessage(SocialMessage.Client message) {
        int playerId = getAuthenticatedPlayerId();
        switch (message) {
            case SocialMessage.SocialAddRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Add(playerId, friendId,
                            SocialRequest.FriendOrFoe.Status.FRIEND));
                }
                if (foeId != null) {
                    playerService.changeSocialRelationship(
                            new SocialRequest.FriendOrFoe.Add(playerId, foeId, SocialRequest.FriendOrFoe.Status.FOE));
                }
            }
            case SocialMessage.SocialRemoveRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    playerService.changeSocialRelationship(
                            new SocialRequest.FriendOrFoe.Remove(playerId, friendId));
                }
                if (foeId != null) {
                    playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(playerId, foeId));
                }
            }
            case SocialMessage.SelectAvatarRequest(String avatarUrl) ->
                    playerService.selectAvatar(new SocialRequest.SelectAvatar(playerId, avatarUrl));
            case SocialMessage.RemoveAvatarRequest _ ->
                    playerService.removeAvatar(new SocialRequest.RemoveAvatar(playerId));
            case SocialMessage.ListAvatarsRequest _ -> {
                Collection<Avatar> avatars = playerService.getAvatars(new SocialRequest.Avatars(playerId));
                connection.sendAndAwait(new SocialMessage.AvatarInfoList(dtoMapper.mapAvatars(avatars)));
            }
        }
    }

    private void handleAdminMessage(AdminMessage.Client message) {
        int playerId = getAuthenticatedPlayerId();
        AdminRequest adminRequest = switch (message) {
            case AdminMessage.BroadcastRequest(String broadcastMessage) ->
                    new AdminRequest.Broadcast(playerId, broadcastMessage);
            case AdminMessage.KickPlayerRequest(int kickPlayerId) ->
                    new AdminRequest.KickPlayer(playerId, kickPlayerId);
            case AdminMessage.ClosePlayerGameRequest(int closePlayerId) ->
                    new AdminRequest.ClosePlayerGame(playerId, closePlayerId);
        };
        adminService.handleRequest(adminRequest);
    }

    private void handleMatchmakerMessage(MatchmakerMessage.Client message) {
        int playerId = getAuthenticatedPlayerId();
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
        int playerId = getAuthenticatedPlayerId();
        switch (message) {
            case GameMessage.HostGameRequest hostGameRequest -> {
                Game game = gameService.createNewGame(playerId, hostGameRequest);
                launchGame(game);
            }
            case GameMessage.JoinGameRequest _ -> {}
            case GameMessage.RestoreGameSessionRequest _ -> {}
        }
    }

    private void handleGpgMessage(GPGMessage.Client message) {
        int playerId = getAuthenticatedPlayerId();
        int gameId = game().orElseThrow().getDetails().id();
        gpgService.handleClientMessage(gameId, playerId, message);
    }

    private int getAuthenticatedPlayerId() {
        if (playerId == null) {
            throw new ClientException("Not authenticated");
        }
        return playerId;
    }

}
