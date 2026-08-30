package com.faforever.server.endpoint.websocket;

import com.faforever.server.connection.SessionController;
import com.faforever.server.message.LobbyMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.TextDecodeException;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.SessionScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@WebSocket(path = "/")
@SessionScoped
@RunOnVirtualThread
public class LobbyWebSocket {

    private final SessionController sessionController;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        sessionController.setConnection(new LobbyJsonWebsocketConnection(connection));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        sessionController.clearConnection();
    }

    @OnTextMessage
    public void onTextMessage(LobbyMessage.Client message) {
        sessionController.handleMessage(message);
    }

    @OnError
    public void onError(TextDecodeException decodeException) {
        LOG.warn("Invalid message received", decodeException);
    }

}
