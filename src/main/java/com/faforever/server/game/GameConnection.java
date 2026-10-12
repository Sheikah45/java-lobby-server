package com.faforever.server.game;


import com.faforever.server.message.client.GPGMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class GameConnection {

    void sendAndAwait(GPGMessage.Server message) {

    }

    void close() {

    }

}
