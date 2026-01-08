package com.faforever.server.connection;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.endpoint.websocket.LobbyJsonWebsocketConnection;
import com.faforever.server.game.Game;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.GPGMessage;
import com.faforever.server.message.GameMessage;
import com.faforever.server.message.LobbyMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.player.Player;
import com.faforever.server.social.Avatar;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.SessionScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@JBossLog
@SessionScoped
@RequiredArgsConstructor
public class SessionController {

    private final BroadcastService broadcastService;

    private final DtoMapper dtoMapper;

    private LobbyConnection connection = NoConnection.getInstance();
    private @Nullable UserAgent userAgent;
    private @Nullable Player player;
    private @Nullable Game game;
    @Getter
    private boolean authenticated;

    private final long sessionId = new Random().nextLong(Long.MAX_VALUE);

    public void close() {
        connection.close();
    }

    public void setConnection(WebSocketConnection webSocketConnection) {
        if (!(connection instanceof NoConnection)) {
            throw new IllegalStateException("Connection already set for session");
        }
        connection = new LobbyJsonWebsocketConnection(webSocketConnection);
        broadcastService.registerSession(this);
    }

    public void clearConnection() {
        if (player != null) {
            player.removeSession(this);
        }
        broadcastService.unregisterSession(this);
        connection = NoConnection.getInstance();
    }

    public void setUserAgent(UserAgent userAgent) {
        if (this.userAgent != null) {
            LOG.warn("User agent already set for session");
            return;
        }
        this.userAgent = userAgent;
    }

    public void setPlayer(Player player) {
        if (this.player != null && !Objects.equals(this.player, player)) {
            throw new IllegalStateException("Player already set for the session");
        }

        this.player = player;
        player.addSession(this);
        connection.sendAndAwait(new ConnectionMessage.LoginSuccessResponse(dtoMapper.map(player)));

        Set<String> channels = new HashSet<>();
        player.getClan().map("#%s_clan"::formatted).ifPresent(channels::add);
        connection.sendAndAwait(new SocialMessage.SocialInfo(channels, player.getFriendIds(), player.getFoeIds()));
        authenticated = true;
    }

    public Player getPlayer() {
        if (player == null) {
            throw new IllegalStateException("Player is not set for session");
        }
        return player;
    }

    public void setGame(Game game) {
        if (this.game != null && !Objects.equals(this.game, game)) {
            throw new IllegalStateException("Game already set for the session");
        }

        this.game = game;
        game.addGameConnection(this);
    }

    public Game getGame() {
        if (game == null) {
            throw new IllegalStateException("Game is not set for session");
        }
        return game;
    }

    public void sendSessionInfo() {
        connection.sendAndAwait(new ConnectionMessage.SessionResponse(sessionId));
    }

    public void pong() {
        connection.sendAndAwait(new ConnectionMessage.Pong());
    }

    public void broadcast(LobbyMessage.Broadcast message) {
        connection.sendAndAwait(message);
    }

    public boolean isActive() {
        return !(connection instanceof NoConnection);
    }

    public void sendAvatars(Collection<Avatar> avatars) {
        connection.sendAndAwait(new SocialMessage.AvatarInfoList(dtoMapper.mapAvatars(avatars)));
    }

    public void launchGame(Game game) {
        GameMessage.GameLaunchResponse gameLaunchResponse = dtoMapper.map(game);
        connection.sendAndAwait(gameLaunchResponse);
    }

    public void sendGpgHostGame() {
        connection.sendAndAwait(new GPGMessage.HostGame(List.of(getGame().getMapName())));
    }

}
