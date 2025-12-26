package com.faforever.server.connection;

import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.session.SessionController;
import com.faforever.server.session.UserAgent;
import com.faforever.server.social.PlayerService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.jose.JWT;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class ConnectionService {

    private final PlayerService playerService;

    private final SessionController sessionController;

    public void handleMessage(ConnectionMessage.Client message) {
        switch (message) {
            case ConnectionMessage.Ping() -> sessionController.pong();
            case ConnectionMessage.Pong() -> {}
            case ConnectionMessage.SessionRequest(String userAgent, String version) -> {
                sessionController.setUserAgent(new UserAgent(userAgent, version));
                sessionController.sendSessionInfo();
            }
            case ConnectionMessage.AuthenticateRequest(String token, String uniqueId) -> {
                JsonObject tokenObject = JWT.parse(token);
                int playerId = Integer.parseInt(tokenObject.getJsonObject("payload").getString("sub"));
                playerService.initializeSessionPlayer(playerId);
            }
        }
    }

}
