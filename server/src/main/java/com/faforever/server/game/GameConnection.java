package com.faforever.server.game;


import com.faforever.server.message.SessionHandler;
import com.faforever.server.message.external.GPGMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class GameConnection {

    private final SessionHandler sessionHandler;


    void sendAndAwait(GPGMessage.Server message) {

    }

    void close() {

    }

}
