package com.faforever.server.connection;

import com.faforever.server.admin.AdminRequest;
import com.faforever.server.admin.AdminService;
import com.faforever.server.broadcast.BroadcastService;
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
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerService;
import com.faforever.server.social.Avatar;
import com.faforever.server.social.SocialRequest;
import com.faforever.server.social.SocialService;
import com.faforever.server.utils.NoThrowCloseable;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.Dependent;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.MDC;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

@JBossLog
@RequiredArgsConstructor
@Dependent
public class SessionController {

    private final BroadcastService broadcastService;
    private final SocialService socialService;
    private final AdminService adminService;
    private final MatchmakerService matchmakerService;
    private final GameService gameService;
    private final PlayerService playerService;
    private final GPGService gpgService;

    private final JWTParser jwtParser;

    private final DtoMapper dtoMapper;

    private LobbyConnection connection = NoConnection.getInstance();
    private @Nullable UserAgent userAgent;
    private @Nullable Player player;
    private @Nullable Game game;

    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    public boolean isAuthenticated() {
        return player != null;
    }

    public void setConnection(LobbyConnection connection) {
        if (!(this.connection instanceof NoConnection)) {
            throw new IllegalStateException("Connection already set for session");
        }
        if (connection instanceof NoConnection) {
            throw new IllegalArgumentException("Connection being set is not active");
        }
        this.connection = connection;
        broadcastService.registerSession(this);
    }

    public void clearConnection() {
        if (player != null) {
            player.removeSession(this);
        }
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

    public Optional<Player> player() {
        return Optional.ofNullable(player);
    }

    public Optional<Game> game() {
        return Optional.ofNullable(game);
    }

    public void broadcast(LobbyMessage.Broadcast message) {
        connection.sendAndAwait(message);
    }

    public boolean isActive() {
        return !(connection instanceof NoConnection);
    }

    void initializePlayer(int playerId) {
        if (this.player != null) {
            throw new IllegalStateException("Player already set for the session");
        }
        LOG.debug("Initializing player");

        player = playerService.getOrCreatePlayer(playerId);;
        player.addSession(this);
        connection.sendAndAwait(new ConnectionMessage.LoginSuccessResponse(dtoMapper.map(player), OffsetDateTime.now()));
        Set<String> channels = new HashSet<>();
        Optional.ofNullable(player.getDetails().clan()).map("#%s_clan"::formatted).ifPresent(channels::add);
        connection.sendAndAwait(new SocialMessage.SocialInfo(channels, player.getFriendIds(), player.getFoeIds()));
    }

    long sessionId() {
        return sessionId;
    }

    private void sendAvatars(Collection<Avatar> avatars) {
        connection.sendAndAwait(new SocialMessage.AvatarInfoList(dtoMapper.mapAvatars(avatars)));
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

    public void sendGpgHostGame() {
        connection.sendAndAwait(new GPGMessage.HostGame(List.of(game().orElseThrow().getMapName())));
    }

    public void kick() {
        connection.sendAndAwait(new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK));
        connection.close();
    }

    public void closeGame() {
        if (game == null) {
            return;
        }
        connection.sendAndAwait(new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL));
    }

    public void handleMessage(LobbyMessage.Client message) {
        try(NoThrowCloseable _ = populateMDC()) {
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
        player()
                         .map(Player::getDetails)
                         .map(Player.Details::id)
                         .ifPresent(playerId -> MDC.put("playerId", playerId));
        game()
                         .map(Game::getDetails)
                         .map(Game.Details::id)
                         .ifPresent(gameId -> MDC.put("gameId", gameId));

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
            case ConnectionMessage.SessionRequest sessionRequest -> {
                setUserAgent(new UserAgent(sessionRequest.userAgent(), sessionRequest.version()));
                connection.sendAndAwait(new ConnectionMessage.SessionResponse(sessionId));
            }
            case ConnectionMessage.AuthenticateRequest authenticateRequest -> {
                String token = authenticateRequest.token();
                JsonWebToken jwt;
                try {
                    jwt = jwtParser.parse(token);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                int playerId = Integer.parseInt(jwt.getSubject());
                initializePlayer(playerId);
                Collection<Player> players = playerService.getOnlinePlayers();
                connection.sendAndAwait(new SocialMessage.PlayerInfoList(dtoMapper.map(players)));
            }
        }
    }

    private void handleSocialMessage(SocialMessage.Client message) {
        Player player = player().orElseThrow();
        int playerId = player.getDetails().id();
        switch (message) {
            case SocialMessage.SocialAddRequest addRequest -> {
                Integer friendId = addRequest.friendId();
                if (friendId != null) {
                    socialService.changeSocialRelationship(new SocialRequest.FriendOrFoe.AddFriend(playerId, friendId));
                    player.addFriend(friendId);
                    player.removeFoe(friendId);
                }
                Integer foeId = addRequest.foeId();
                if (foeId != null) {
                    socialService.changeSocialRelationship(new SocialRequest.FriendOrFoe.AddFoe(playerId, foeId));
                    player.addFoe(foeId);
                    player.removeFriend(foeId);
                }
            }
            case SocialMessage.SocialRemoveRequest removeRequest -> {
                Integer friendId = removeRequest.friendId();
                if (friendId != null) {
                    socialService.changeSocialRelationship(
                            new SocialRequest.FriendOrFoe.RemoveFriend(playerId, friendId));
                    player.removeFriend(friendId);
                }
                Integer foeId = removeRequest.foeId();
                if (foeId != null) {
                    socialService.changeSocialRelationship(new SocialRequest.FriendOrFoe.RemoveFoe(playerId, foeId));
                    player.removeFoe(foeId);
                }
            }
            case SocialMessage.SelectAvatarRequest selectRequest ->
                    socialService.selectAvatar(new SocialRequest.SelectAvatar(playerId, selectRequest.avatarUrl()));
            case SocialMessage.ListAvatarsRequest _ ->
                    sendAvatars(socialService.getAvatars(new SocialRequest.Avatars(playerId)));
        }
    }

    private void handleAdminMessage(AdminMessage.Client message) {
        int playerId = player().orElseThrow().getDetails().id();
        AdminRequest adminRequest = switch (message) {
            case AdminMessage.BroadcastRequest broadcastRequest ->
                    new AdminRequest.Broadcast(playerId, broadcastRequest.message());
            case AdminMessage.KickPlayerRequest kickPlayerRequest ->
                    new AdminRequest.KickPlayer(playerId, kickPlayerRequest.playerId());
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest ->
                    new AdminRequest.ClosePlayerGame(playerId, closePlayerGameRequest.playerId());
        };
        adminService.handleRequest(adminRequest);
    }

    private void handleMatchmakerMessage(MatchmakerMessage.Client message) {
        int playerId = player().orElseThrow().getDetails().id();
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
        int playerId = player().orElseThrow().getDetails().id();
        switch (message) {
            case GameMessage.HostGameRequest hostGameRequest -> {
                Game game = gameService.createNewGame(player().orElseThrow(), hostGameRequest);
                launchGame(game);
            }
            case GameMessage.JoinGameRequest _ -> {}
            case GameMessage.RestoreGameSessionRequest _ -> {}
        }
    }

    private void handleGpgMessage(GPGMessage.Client message) {
        int gameId = game().orElseThrow().getDetails().id();
        int playerId = player().orElseThrow().getDetails().id();
        gpgService.handleClientMessage(gameId, playerId, message);
    }

}
