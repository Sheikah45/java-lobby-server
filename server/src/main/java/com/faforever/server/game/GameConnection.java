package com.faforever.server.game;


import com.faforever.server.connection.SessionController;
import com.faforever.server.message.GPGMessage;
import com.faforever.server.player.Player;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class GameConnection {

    private final SessionController sessionController;


    void sendAndAwait(GPGMessage.Server message) {

    }

    void close() {

    }

}
