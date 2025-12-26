package com.faforever.server.session;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.LobbyConnection;
import com.faforever.server.connection.NoConnection;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.LobbyMessage;
import com.faforever.server.social.Player;
import com.faforever.server.websocket.LobbyJsonWebsocketConnection;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.SessionScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Random;

@JBossLog
@SessionScoped
@RequiredArgsConstructor
public class SessionController {

    private final BroadcastService broadcastService;

    private LobbyConnection connection = NoConnection.getInstance();
    private @Nullable UserAgent userAgent;
    private @Nullable Player player;

    private final long sessionId = new Random().nextLong(Long.MAX_VALUE);

    public void setConnection(WebSocketConnection webSocketConnection) {
        if (!(connection instanceof NoConnection)) {
            throw new IllegalStateException("Connection already set for session");
        }
        connection = new LobbyJsonWebsocketConnection(webSocketConnection);
        broadcastService.registerSession(this);
    }

    public void clearConnection() {
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
        connection.sendAndAwait(new ConnectionMessage.LoginSuccessResponse(player.asPlayerInfo()));
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

}
