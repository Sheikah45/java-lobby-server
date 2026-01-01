package com.faforever.server.connection;

import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.social.PlayerService;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class ConnectionService {

    private final PlayerService playerService;

    private final SessionController sessionController;

    private final JWTParser jwtParser;

    public void authenticate(ConnectionMessage.AuthenticateRequest authenticateRequest) {
        String token = authenticateRequest.token();
        JsonWebToken jwt;
        try {
            jwt = jwtParser.parse(token);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        int playerId = Integer.parseInt(jwt.getSubject());
        playerService.initializeSessionPlayer(playerId);
    }

    public void updateSession(ConnectionMessage.SessionRequest sessionRequest) {
        sessionController.setUserAgent(new UserAgent(sessionRequest.userAgent(), sessionRequest.version()));
        sessionController.sendSessionInfo();
    }

    public void pong() {
        sessionController.pong();
    }

}
