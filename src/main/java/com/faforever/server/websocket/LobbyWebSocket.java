package com.faforever.server.websocket;

import com.faforever.server.message.LobbyMessage;
import com.faforever.server.message.MessageBroker;
import com.faforever.server.session.SessionController;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.TextDecodeException;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@WebSocket(path = "/v1/json")
@ApplicationScoped
@RunOnVirtualThread
public class LobbyWebSocket {

    private final SessionController sessionController;
    private final MessageBroker messageBroker;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        sessionController.setConnection(connection);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        sessionController.clearConnection();
    }

    @OnTextMessage
    public void onTextMessage(LobbyMessage.Client message) {
        messageBroker.handleMessage(message);
    }

    @OnError
    public void onError(TextDecodeException decodeException) {
        LOG.warn("Invalid message received", decodeException);
    }

}
