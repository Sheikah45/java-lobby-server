package com.faforever.server.connection;

public class SessionControllerTestUtils {

    private SessionControllerTestUtils() {}

    public static void setSessionPlayer(SessionController sessionController, int playerId) {
        sessionController.setPlayerId(playerId);
    }

}
